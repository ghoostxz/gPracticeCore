package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class MenuManager {
    public abstract static class GuiHolder implements InventoryHolder {
        protected Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    /** Menu 1: escolha do modo. */
    public static final class MenuHolder extends GuiHolder {
        private final boolean ranked;

        public MenuHolder(boolean ranked) { this.ranked = ranked; }

        public boolean ranked() { return ranked; }
    }

    /** Menu 2: escolha do formato (solo/dupla/trio/quarteto) do modo clicado. */
    public static final class SizeHolder extends GuiHolder {
        private final Mode mode;
        private final boolean ranked;

        public SizeHolder(Mode mode, boolean ranked) {
            this.mode = mode;
            this.ranked = ranked;
        }

        public Mode mode() { return mode; }

        public boolean ranked() { return ranked; }
    }

    public record SizeOption(int size, String name, Material icon) {}

    private final GCelestialPracticeCore plugin;
    private final NamespacedKey modeKey;
    private final NamespacedKey sizeKey;

    public MenuManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "gcpc_mode");
        this.sizeKey = new NamespacedKey(plugin, "gcpc_size");
    }

    public List<SizeOption> sizes() {
        List<SizeOption> out = new ArrayList<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("sizes");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(key);
                if (c == null) continue;
                Material icon = Material.matchMaterial(c.getString("icon", "IRON_SWORD"));
                if (icon == null) icon = Material.IRON_SWORD;
                int size = Math.max(1, Math.min(8, c.getInt("size", 1)));
                out.add(new SizeOption(size, c.getString("name", key), icon));
            }
        }
        if (out.isEmpty()) out.add(new SizeOption(1, "&aSolo &7(1v1)", Material.IRON_SWORD));
        return out;
    }

    private ItemStack filler() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.space());
        filler.setItemMeta(fm);
        return filler;
    }

    /** Atualiza a cada segundo os menus de modo abertos (contagens em batalha / na fila). */
    public void startRefresh() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (top.getHolder(false) instanceof MenuHolder h) populate(top, p, h.ranked());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void open(Player p, boolean ranked) {
        MenuHolder holder = new MenuHolder(ranked);
        String title = plugin.getConfig().getString(ranked ? "menu.ranked-title" : "menu.unranked-title", "Fila");
        Inventory inv = Bukkit.createInventory(holder, 36, Text.c(title));
        holder.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler());
        populate(inv, p, ranked);
        p.openInventory(inv);
    }

    private void populate(Inventory inv, Player p, boolean ranked) {
        int i = 0;
        for (Mode mode : plugin.modes().all()) {
            int slot = i < 7 ? 10 + i : 19 + (i - 7);
            if (slot > 25) break;
            inv.setItem(slot, modeItem(p, mode, ranked));
            i++;
        }
        // Linha de baixo: totais do tipo de fila deste menu
        inv.setItem(30, infoItem(Material.DIAMOND_SWORD,
                "&cEm batalha: &f" + plugin.duels().fighting(null, ranked),
                "&7Jogadores lutando agora."));
        inv.setItem(32, infoItem(Material.CLOCK,
                "&aNa fila: &f" + plugin.queue().countAll(ranked),
                "&7Jogadores aguardando partida."));
    }

    private ItemStack infoItem(Material mat, String name, String lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(name));
        m.lore(List.of(Text.c(lore)));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    public void openSizes(Player p, Mode mode, boolean ranked) {
        SizeHolder holder = new SizeHolder(mode, ranked);
        String title = plugin.getConfig().getString("menu.size-title", "&8{mode}")
                .replace("{mode}", mode.displayName());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(title));
        holder.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler());

        int i = 0;
        for (SizeOption opt : sizes()) {
            if (i >= 4) break;
            inv.setItem(10 + i * 2, sizeItem(p, mode, ranked, opt));
            i++;
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(Text.c("&cVoltar"));
        bm.getPersistentDataContainer().set(sizeKey, PersistentDataType.STRING, "back");
        back.setItemMeta(bm);
        inv.setItem(22, back);
        p.openInventory(inv);
    }

    private ItemStack modeItem(Player p, Mode mode, boolean ranked) {
        ItemStack it = new ItemStack(mode.icon());
        int fighting = plugin.duels().fighting(mode, ranked);
        it.setAmount(Math.max(1, Math.min(64, fighting))); // quantidade do item = jogadores em batalha neste modo
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(mode.displayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("&7Em batalha: &f" + fighting));
        lore.add(Text.c("&7Na fila: &f" + plugin.queue().count(mode, ranked)));
        if (ranked) {
            int pts = plugin.data().points(p.getUniqueId(), mode.id());
            lore.add(Text.c("&7Seu rank: " + plugin.ranks().colored(pts) + " &8(" + pts + " pts)"));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&eClique para escolher o formato"));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.id());
        it.setItemMeta(m);
        return it;
    }

    private ItemStack sizeItem(Player p, Mode mode, boolean ranked, SizeOption opt) {
        ItemStack it = new ItemStack(opt.icon());
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(opt.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("&7Jogadores por time: &f" + opt.size()));
        lore.add(Text.c("&7Na fila: &f" + plugin.queue().count(mode, ranked, opt.size())));
        if (ranked) {
            int pts = plugin.data().points(p.getUniqueId(), mode.id());
            lore.add(Text.c("&7Seu rank: " + plugin.ranks().colored(pts) + " &8(" + pts + " pts)"));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&eClique para entrar na fila"));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(sizeKey, PersistentDataType.STRING, String.valueOf(opt.size()));
        it.setItemMeta(m);
        return it;
    }

    public String modeId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
    }

    /** "back" ou o numero de jogadores por time. */
    public String sizeValue(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(sizeKey, PersistentDataType.STRING);
    }
}
