package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /duelo <jogador> (/duelar): desafio 1v1 normal com menu de modalidade e aceite por comando ou clique no chat. */
public final class ChallengeManager {
    public static final class ChallengeHolder extends MenuManager.GuiHolder {
        private final UUID target;

        ChallengeHolder(UUID target) { this.target = target; }

        public UUID target() { return target; }
    }

    private record Request(UUID challenger, UUID target, Mode mode, long expires) {}

    private final GCelestialPracticeCore plugin;
    private final NamespacedKey modeKey;
    /** alvo -> (desafiante -> pedido) */
    private final Map<UUID, Map<UUID, Request>> byTarget = new HashMap<>();

    public ChallengeManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "gcpc_mode"); // mesma chave do menu de filas
    }

    private boolean available(Mode m) {
        return plugin.modes().hasKit(m) && plugin.arenas().hasArena(m.id());
    }

    // ------------------------------------------------------------------ menu

    public void openMenu(Player challenger, Player target) {
        Messages msg = plugin.messages();
        if (challenger.equals(target)) { msg.send(challenger, "duel-self"); return; }
        if (plugin.duels().isInDuel(challenger)) { msg.send(challenger, "in-duel"); return; }
        if (plugin.queue().isQueued(challenger)) { msg.send(challenger, "duel-leave-queue"); return; }
        if (plugin.duels().isInDuel(target)) { msg.send(challenger, "duel-target-busy", "player", target.getName()); return; }

        List<Mode> modes = new ArrayList<>();
        for (Mode m : plugin.modes().all()) if (available(m)) modes.add(m);
        if (modes.isEmpty()) { msg.send(challenger, "duel-no-modes"); return; }

        ChallengeHolder holder = new ChallengeHolder(target.getUniqueId());
        String title = plugin.getConfig().getString("menu.challenge-title", "&8Desafiar {player}")
                .replace("{player}", target.getName());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(title));
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.space());
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        int i = 0;
        for (Mode mode : modes) {
            if (i >= 7) break;
            ItemStack it = new ItemStack(mode.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.c(mode.displayName()));
            m.lore(Text.c(List.of("&7Duelo 1v1 contra &f" + target.getName(), "", "&eClique para desafiar")));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            m.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.id());
            it.setItemMeta(m);
            inv.setItem(10 + i, it);
            i++;
        }
        challenger.openInventory(inv);
    }

    // --------------------------------------------------------------- convite

    public void send(Player challenger, UUID targetId, String modeId) {
        Messages msg = plugin.messages();
        Player target = Bukkit.getPlayer(targetId);
        Mode mode = plugin.modes().get(modeId);
        if (target == null || !target.isOnline()) { msg.send(challenger, "player-not-found"); return; }
        if (mode == null || !available(mode)) { msg.send(challenger, "duel-no-modes"); return; }
        if (plugin.duels().isInDuel(challenger)) { msg.send(challenger, "in-duel"); return; }
        if (plugin.queue().isQueued(challenger)) { msg.send(challenger, "duel-leave-queue"); return; }
        if (plugin.duels().isInDuel(target)) { msg.send(challenger, "duel-target-busy", "player", target.getName()); return; }

        long seconds = plugin.getConfig().getLong("challenge.expire-seconds", 30L);
        byTarget.computeIfAbsent(targetId, k -> new HashMap<>()).put(challenger.getUniqueId(),
                new Request(challenger.getUniqueId(), targetId, mode, System.currentTimeMillis() + seconds * 1000L));

        msg.send(challenger, "duel-request-sent", "player", target.getName(),
                "mode", mode.displayName(), "seconds", String.valueOf(seconds));
        Component c = msg.get("duel-request-received", "player", challenger.getName(), "mode", mode.displayName())
                .clickEvent(ClickEvent.runCommand("/dueloaceitar " + challenger.getName()))
                .hoverEvent(HoverEvent.showText(Text.c("&aClique para aceitar o duelo")));
        target.sendMessage(c);
    }

    /** /dueloaceitar [jogador]: sem nome, aceita o convite mais recente. */
    public void accept(Player target, String challengerName) {
        Messages msg = plugin.messages();
        Map<UUID, Request> pending = byTarget.get(target.getUniqueId());
        if (pending != null) pending.values().removeIf(r -> r.expires() < System.currentTimeMillis());
        if (pending == null || pending.isEmpty()) { msg.send(target, "duel-request-none"); return; }

        Request req = null;
        if (challengerName != null) {
            Player c = Bukkit.getPlayerExact(challengerName);
            if (c != null) req = pending.get(c.getUniqueId());
        } else {
            for (Request r : pending.values()) if (req == null || r.expires() > req.expires()) req = r;
        }
        if (req == null) { msg.send(target, "duel-request-none"); return; }

        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger == null || !challenger.isOnline()
                || plugin.duels().isInDuel(challenger) || plugin.duels().isInDuel(target)) {
            pending.remove(req.challenger());
            msg.send(target, "duel-request-invalid");
            return;
        }

        purge(target.getUniqueId());
        purge(challenger.getUniqueId());
        plugin.queue().leave(challenger, false);
        plugin.queue().leave(target, false);
        plugin.duels().start(List.of(challenger), List.of(target), req.mode(), false, 1);
    }

    /** Remove convites enviados e recebidos por um jogador (saiu do servidor / entrou em duelo). */
    public void purge(UUID id) {
        byTarget.remove(id);
        for (Map<UUID, Request> m : byTarget.values()) m.remove(id);
    }
}
