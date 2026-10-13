package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyManager {
    private final GCelestialPracticeCore plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();

    public PartyManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    public Party get(Player p) { return byMember.get(p.getUniqueId()); }

    public int max() { return Math.max(2, plugin.getConfig().getInt("party.max-size", 4)); }

    public String privacy(Party party) { return party.isOpen() ? "&aPublica" : "&cPrivada"; }

    /** Parties publicas com vaga. */
    public List<Party> openParties() {
        Set<Party> set = new LinkedHashSet<>(byMember.values());
        List<Party> out = new ArrayList<>();
        for (Party p : set) if (p.isOpen() && p.size() < max()) out.add(p);
        return out;
    }

    private void refresh(List<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) plugin.lobby().updateDynamic(m);
        }
    }

    private void broadcast(Party party, String key, String... kv) {
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) plugin.messages().send(m, key, kv);
        }
    }

    /** Se a party esta na fila e algo muda (entrada/saida/remocao), a fila e cancelada. */
    private void cancelQueue(Party party) {
        boolean any = false;
        for (UUID id : new ArrayList<>(party.members())) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && plugin.queue().isQueued(m)) {
                plugin.queue().leave(m, true);
                any = true;
            }
        }
        if (any) broadcast(party, "party-queue-cancelled");
    }

    public Party create(Player p) {
        Messages msg = plugin.messages();
        Party existing = get(p);
        if (existing != null) { msg.send(p, "party-already"); return existing; }
        if (plugin.duels().isInDuel(p)) { msg.send(p, "in-duel"); return null; }
        plugin.queue().leave(p, true);
        Party party = new Party(p.getUniqueId(), plugin.getConfig().getBoolean("party.default-public", false));
        byMember.put(p.getUniqueId(), party);
        plugin.lobby().updateDynamic(p);
        msg.send(p, "party-created", "privacy", privacy(party));
        return party;
    }

    public void invite(Player leader, Player target) {
        Messages msg = plugin.messages();
        Party party = get(leader);
        if (party == null) {
            party = create(leader);
            if (party == null) return;
        }
        if (!party.isLeader(leader.getUniqueId())) { msg.send(leader, "party-leader-only"); return; }
        if (target.equals(leader)) { msg.send(leader, "party-self-invite"); return; }
        if (get(target) != null) { msg.send(leader, "party-target-in-party", "player", target.getName()); return; }
        if (party.size() >= max()) { msg.send(leader, "party-full"); return; }

        long ttl = plugin.getConfig().getLong("party.invite-expire-seconds", 60L) * 1000L;
        party.invites().put(target.getUniqueId(), System.currentTimeMillis() + ttl);
        msg.send(leader, "party-invite-sent", "player", target.getName());
        Component c = msg.get("party-invite-received", "leader", leader.getName())
                .clickEvent(ClickEvent.runCommand("/party accept " + leader.getName()))
                .hoverEvent(HoverEvent.showText(Text.c("&aClique para aceitar")));
        target.sendMessage(c);
    }

    public void accept(Player p, UUID leaderId) {
        Messages msg = plugin.messages();
        Party party = byMember.get(leaderId);
        if (party == null || !party.isLeader(leaderId) || !party.hasValidInvite(p.getUniqueId())) {
            msg.send(p, "party-invite-none");
            return;
        }
        addChecked(p, party);
    }

    /** Entrar numa party publica (ou privada, se tiver convite). */
    public void join(Player p, UUID leaderId) {
        Messages msg = plugin.messages();
        Party party = byMember.get(leaderId);
        if (party == null || !party.isLeader(leaderId)) { msg.send(p, "party-player-not-found"); return; }
        if (!party.isOpen() && !party.hasValidInvite(p.getUniqueId())) { msg.send(p, "party-private"); return; }
        addChecked(p, party);
    }

    private void addChecked(Player p, Party party) {
        Messages msg = plugin.messages();
        if (get(p) != null) { msg.send(p, "party-already"); return; }
        if (plugin.duels().isInDuel(p)) { msg.send(p, "in-duel"); return; }
        if (party.size() >= max()) { msg.send(p, "party-full"); return; }
        plugin.queue().leave(p, true);   // sai da fila solo
        cancelQueue(party);              // a party mudou
        party.members().add(p.getUniqueId());
        party.invites().remove(p.getUniqueId());
        byMember.put(p.getUniqueId(), party);
        broadcast(party, "party-joined", "player", p.getName());
        refresh(party.members());
    }

    public void kick(Player leader, UUID targetId) {
        Messages msg = plugin.messages();
        Party party = get(leader);
        if (party == null) { msg.send(leader, "party-not-in"); return; }
        if (!party.isLeader(leader.getUniqueId())) { msg.send(leader, "party-leader-only"); return; }
        if (targetId.equals(leader.getUniqueId()) || !party.contains(targetId)) {
            msg.send(leader, "party-player-not-found");
            return;
        }
        cancelQueue(party);
        Player t = Bukkit.getPlayer(targetId);
        party.members().remove(targetId);
        byMember.remove(targetId);
        if (t != null) msg.send(t, "party-you-kicked");
        broadcast(party, "party-kicked", "player", t != null ? t.getName() : "?");
        if (t != null) plugin.lobby().updateDynamic(t);
    }

    public void leave(Player p, boolean quit) {
        Party party = get(p);
        if (party == null) {
            if (!quit) plugin.messages().send(p, "party-not-in");
            return;
        }
        cancelQueue(party);
        party.members().remove(p.getUniqueId());
        byMember.remove(p.getUniqueId());
        if (!quit) {
            plugin.messages().send(p, "party-you-left");
            plugin.lobby().updateDynamic(p);
        }
        if (party.members().isEmpty()) return;
        if (party.isLeader(p.getUniqueId())) {
            party.setLeader(party.members().get(0));
            Player nl = Bukkit.getPlayer(party.leader());
            broadcast(party, "party-new-leader", "player", nl != null ? nl.getName() : "?");
        }
        broadcast(party, "party-left", "player", p.getName());
        refresh(party.members()); // novo lider troca "Sair" por "Destruir"
    }

    public void disband(Player leader) {
        Messages msg = plugin.messages();
        Party party = get(leader);
        if (party == null) { msg.send(leader, "party-not-in"); return; }
        if (!party.isLeader(leader.getUniqueId())) { msg.send(leader, "party-leader-only"); return; }
        cancelQueue(party);
        for (UUID id : new ArrayList<>(party.members())) {
            byMember.remove(id);
            Player m = Bukkit.getPlayer(id);
            if (m != null) {
                msg.send(m, "party-disbanded");
                plugin.lobby().updateDynamic(m);
            }
        }
        party.members().clear();
    }

    public void setOpen(Player leader, boolean open) {
        Messages msg = plugin.messages();
        Party party = get(leader);
        if (party == null) { msg.send(leader, "party-not-in"); return; }
        if (!party.isLeader(leader.getUniqueId())) { msg.send(leader, "party-leader-only"); return; }
        party.setOpen(open);
        broadcast(party, open ? "party-now-public" : "party-now-private");
    }

    public void info(Player p) {
        Messages msg = plugin.messages();
        Party party = get(p);
        if (party == null) { msg.send(p, "party-not-in"); return; }
        p.sendMessage(msg.raw("party-info-header", "privacy", privacy(party),
                "count", String.valueOf(party.size()), "max", String.valueOf(max())));
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            p.sendMessage(msg.raw("party-info-member", "player", m != null ? m.getName() : "?",
                    "role", party.isLeader(id) ? "&6(Lider)" : ""));
        }
    }
}
