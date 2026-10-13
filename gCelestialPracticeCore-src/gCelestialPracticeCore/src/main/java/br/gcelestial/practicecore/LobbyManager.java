package br.gcelestial.practicecore;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LobbyManager {
    /** Itens entregues ao entrar (a ordem define quem vence em caso de slots repetidos). */
    private static final List<String> HOTBAR_ITEMS = List.of("unranked", "ranked", "party", "kit-editor");
    private static final List<String> ALL_ITEMS =
            List.of("unranked", "ranked", "party", "kit-editor", "leave-queue", "party-disband", "party-leave");
    /** Itens que dependem do estado do jogador (fila / party). */
    private static final List<String> DYNAMIC_ITEMS = List.of("leave-queue", "party-disband", "party-leave");

    private final GCelestialPracticeCore plugin;
    private final NamespacedKey key;

    public LobbyManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "gcpc_item");
    }

    public ItemStack build(String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hotbar." + id);
        if (s == null) return null;
        Material m = Material.matchMaterial(s.getString("material", "STONE_SWORD"));
        if (m == null) m = Material.STONE_SWORD;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(s.getString("name", id)));
        meta.lore(Text.c(s.getStringList("lore")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Slot (0-8) configurado em hotbar.<id>.slot */
    public int slot(String id) {
        int s = plugin.getConfig().getInt("hotbar." + id + ".slot", 0);
        return Math.max(0, Math.min(8, s));
    }

    /** Avisa no console se dois itens da hotbar usam o mesmo slot. */
    public void validateSlots() {
        Map<Integer, String> used = new HashMap<>();
        for (String id : ALL_ITEMS) {
            int s = slot(id);
            if (id.equals("party-leave") && s == slot("party-disband")) continue; // mutuamente exclusivos
            String other = used.put(s, id);
            if (other != null) {
                plugin.getLogger().warning("Hotbar: os itens '" + other + "' e '" + id
                        + "' usam o mesmo slot (" + s + "). Ajuste hotbar.<item>.slot no config.yml.");
            }
        }
    }

    public String itemId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void giveHotbar(Player p) {
        for (String id : HOTBAR_ITEMS) {
            ItemStack it = build(id);
            if (it != null) p.getInventory().setItem(slot(id), it);
        }
    }

    /** Recoloca os itens do lobby (usado no /gcpc reload). */
    public void refreshHotbar(Player p) {
        if (plugin.duels().isInDuel(p)) return;
        if (p.hasPermission("gcelestial.admin") && p.getGameMode() == GameMode.CREATIVE) return;
        p.getInventory().clear();
        giveHotbar(p);
        updateDynamic(p);
        p.updateInventory();
    }

    /**
     * Atualiza os itens que dependem do estado:
     * "Sair da fila" enquanto estiver em fila; "Destruir party" (lider) ou "Sair da party" (membro) enquanto em party.
     * Os dois grupos podem aparecer ao mesmo tempo.
     */
    public void updateDynamic(Player p) {
        if (plugin.duels().isInDuel(p)) return;
        for (int i = 0; i < 9; i++) {
            String id = itemId(p.getInventory().getItem(i));
            if (id != null && DYNAMIC_ITEMS.contains(id)) p.getInventory().setItem(i, null);
        }
        giveHotbar(p); // recoloca itens fixos caso algum slot tenha sido compartilhado
        if (plugin.queue().isQueued(p)) setDynamic(p, "leave-queue");
        Party party = plugin.parties().get(p);
        if (party != null) setDynamic(p, party.isLeader(p.getUniqueId()) ? "party-disband" : "party-leave");
        p.updateInventory();
    }

    private void setDynamic(Player p, String id) {
        ItemStack it = build(id);
        if (it != null) p.getInventory().setItem(slot(id), it);
    }

    public void teleportSpawn(Player p) {
        Location s = plugin.data().getSpawn();
        p.teleport(s != null ? s : p.getServer().getWorlds().get(0).getSpawnLocation());
        p.setFallDistance(0);
    }

    /** Reseta o jogador, leva ao spawn do practice e entrega a hotbar. */
    public void sendToLobby(Player p) {
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setAbsorptionAmount(0);
        p.setFallDistance(0);
        p.setGameMode(plugin.gamemode("gamemode.lobby", GameMode.ADVENTURE));
        teleportSpawn(p);
        giveHotbar(p);
        updateDynamic(p);
        p.updateInventory();
    }
}
