package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurador de inventario: o jogador escolhe o modo e REORGANIZA os itens do kit
 * (hotbar + mochila). Nenhum item e criado ou removido: so troca de posicao.
 * A interacao e por "clique para selecionar, clique em outro slot para trocar",
 * entao nenhum item real e movido (sem risco de duplicar ou levar itens).
 */
public final class LayoutMenu {
    public static final class ModeHolder extends MenuManager.GuiHolder {
    }

    public static final class EditorHolder extends MenuManager.GuiHolder {
        final Mode mode;
        final ItemStack[] base;   // itens do kit nos slots 0-35
        final int[] src = new int[36]; // src[slotDoJogador] = slot original do kit (-1 = vazio)
        final int hash;
        int selected = -1;
        boolean saved;

        EditorHolder(Mode mode, ItemStack[] base, int hash) {
            this.mode = mode;
            this.base = base;
            this.hash = hash;
        }
    }

    private static final int SAVE_SLOT = 49;
    private static final int RESET_SLOT = 47;

    private final GCelestialPracticeCore plugin;
    private final NamespacedKey key;

    public LayoutMenu(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "gcpc_layout");
    }

    // slot do jogador (0-8 hotbar, 9-35 mochila) <-> posicao no menu
    private static int slotToGui(int s) { return s >= 9 ? s - 9 : s + 36; }

    private static int guiToSlot(int g) {
        if (g >= 0 && g <= 26) return g + 9;
        if (g >= 36 && g <= 44) return g - 36;
        return -1;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        meta.lore(Text.c(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack glass() {
        return named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    // ------------------------------------------------------- escolha do modo

    public void openModes(Player p) {
        ModeHolder holder = new ModeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Text.c(plugin.getConfig().getString("menu.layout-title", "&8Editar layout do kit")));
        holder.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass());

        int i = 0;
        for (Mode mode : plugin.modes().all()) {
            if (i >= 7) break;
            boolean hasKit = plugin.modes().hasKit(mode);
            ItemStack it = new ItemStack(mode.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.c(mode.displayName()));
            m.lore(Text.c(hasKit
                    ? List.of("&7Reorganize a hotbar e o inventario", "&7deste modo.", "", "&eClique para editar")
                    : List.of("&cKit ainda nao configurado.")));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, mode.id());
            it.setItemMeta(m);
            inv.setItem(10 + i, it);
            i++;
        }
        p.openInventory(inv);
    }

    public String modeAction(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- editor

    public void openEditor(Player p, String modeId) {
        Mode mode = plugin.modes().get(modeId);
        if (mode == null) return;
        if (!plugin.modes().hasKit(mode)) {
            plugin.messages().send(p, "kit-layout-nokit", "mode", mode.displayName());
            return;
        }
        ItemStack[] base = plugin.modes().kitStorage(mode);
        int hash = plugin.modes().kitHash(mode);
        EditorHolder h = new EditorHolder(mode, base, hash);

        int[] saved = plugin.data().getLayout(p.getUniqueId(), mode.id(), hash);
        for (int s = 0; s < 36; s++) h.src[s] = saved != null ? saved[s] : (base[s] != null ? s : -1);

        String title = plugin.getConfig().getString("menu.layout-editor-title", "&8Layout: {mode}")
                .replace("{mode}", mode.displayName());
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(title));
        h.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass());
        inv.setItem(31, named(Material.ARROW, "&7Hotbar &8(abaixo)", List.of()));
        inv.setItem(RESET_SLOT, named(Material.HOPPER, "&eRestaurar padrao",
                List.of("&7Volta os itens as posicoes originais", "&7do kit.")));
        inv.setItem(SAVE_SLOT, named(Material.EMERALD, "&a&lSalvar",
                List.of("&7Salva o layout e fecha o menu.", "&8(Fechar o menu tambem salva)")));
        render(inv, h);
        p.openInventory(inv);
        plugin.messages().send(p, "kit-layout-hint");
    }

    private void render(Inventory inv, EditorHolder h) {
        for (int s = 0; s < 36; s++) {
            int src = h.src[s];
            ItemStack it = (src >= 0 && h.base[src] != null) ? h.base[src].clone() : null;
            if (it != null && h.selected == s) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                lore.add(Component.empty());
                lore.add(Text.c("&e▶ Selecionado: clique em outro slot para trocar"));
                m.lore(lore);
                it.setItemMeta(m);
            }
            inv.setItem(slotToGui(s), it);
        }
    }

    public void clickEditor(Player p, EditorHolder h, int rawSlot) {
        Inventory inv = h.getInventory();
        if (rawSlot == SAVE_SLOT) {
            save(p, h);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        if (rawSlot == RESET_SLOT) {
            for (int s = 0; s < 36; s++) h.src[s] = h.base[s] != null ? s : -1;
            h.selected = -1;
            render(inv, h);
            plugin.messages().send(p, "kit-layout-reset");
            return;
        }
        int s = guiToSlot(rawSlot);
        if (s < 0) return;
        if (h.selected == -1) {
            if (h.src[s] >= 0) h.selected = s;
        } else if (h.selected == s) {
            h.selected = -1;
        } else {
            int tmp = h.src[s];
            h.src[s] = h.src[h.selected];
            h.src[h.selected] = tmp;
            h.selected = -1;
        }
        render(inv, h);
    }

    private void save(Player p, EditorHolder h) {
        boolean identity = true;
        for (int s = 0; s < 36; s++) {
            if (h.src[s] != (h.base[s] != null ? s : -1)) { identity = false; break; }
        }
        if (identity) plugin.data().clearLayout(p.getUniqueId(), h.mode.id());
        else plugin.data().setLayout(p.getUniqueId(), h.mode.id(), h.src, h.hash);
        h.saved = true;
        plugin.messages().send(p, "kit-layout-saved", "mode", h.mode.displayName());
    }

    /** Fechar o menu salva automaticamente. */
    public void onClose(Player p, EditorHolder h) {
        if (!h.saved) save(p, h);
    }
}
