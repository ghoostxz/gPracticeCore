package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class DuelManager {
    private final GCelestialPracticeCore plugin;
    private final Map<UUID, Duel> byPlayer = new HashMap<>();
    private final Set<Duel> active = new HashSet<>();
    private final Random random = new Random();

    public DuelManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    public Duel get(Player p) { return byPlayer.get(p.getUniqueId()); }

    public boolean isInDuel(Player p) { return byPlayer.containsKey(p.getUniqueId()); }

    /** Jogadores lutando agora. mode == null conta todos os modos. */
    public int fighting(Mode mode, boolean ranked) {
        int n = 0;
        for (Duel d : active) {
            if (d.ranked != ranked || d.state == Duel.State.ENDED) continue;
            if (mode != null && !d.mode.id().equals(mode.id())) continue;
            for (UUID id : d.all) if (!d.left.contains(id)) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- helpers

    private String names(Duel d, int team) {
        List<String> n = new ArrayList<>();
        for (UUID id : d.team(team)) {
            Player p = Bukkit.getPlayer(id);
            n.add(p != null ? p.getName() : "?");
        }
        return String.join(", ", n);
    }

    private void broadcast(Duel d, String key, String... kv) {
        for (UUID id : d.all) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.messages().send(p, key, kv);
        }
    }

    // ----------------------------------------------------------------- inicio

    public void start(List<Player> t1, List<Player> t2, Mode mode, boolean ranked, int size) {
        List<Player> everyone = new ArrayList<>(t1);
        everyone.addAll(t2);
        for (Player p : everyone) plugin.queue().leave(p, true);
        Messages msg = plugin.messages();

        var list = plugin.arenas().of(mode.id());
        if (list.isEmpty()) {
            for (Player p : everyone) msg.send(p, "no-arena", "mode", mode.displayName());
            return;
        }
        Arena arena = list.get(random.nextInt(list.size()));
        int slot = plugin.arenas().allocateSlot();
        ArenaManager.Pasted pasted;
        try {
            pasted = plugin.arenas().paste(arena, slot);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao colar arena " + arena.name(), ex);
            plugin.arenas().freeSlot(slot);
            for (Player p : everyone) msg.send(p, "arena-error");
            return;
        }
        Location s1 = plugin.arenas().toLocation(pasted, arena.p1());
        Location s2 = plugin.arenas().toLocation(pasted, arena.p2());
        List<UUID> ids1 = new ArrayList<>();
        List<UUID> ids2 = new ArrayList<>();
        for (Player p : t1) ids1.add(p.getUniqueId());
        for (Player p : t2) ids2.add(p.getUniqueId());
        Duel d = new Duel(ids1, ids2, mode, arena, ranked, size, pasted, s1, s2);
        for (UUID id : d.all) byPlayer.put(id, d);
        active.add(d);

        String type = (ranked ? "Ranqueada " : "Normal ") + size + "v" + size;
        for (Player p : everyone) {
            int other = d.teamOf(p.getUniqueId()) == 1 ? 2 : 1;
            msg.send(p, "match-found", "opponent", names(d, other), "mode", mode.displayName(), "type", type);
        }
        startRound(d);
    }

    private void prepare(Player p, Duel d) {
        p.setGameMode(plugin.gamemode("gamemode.duel", GameMode.SURVIVAL));
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setAbsorptionAmount(0);
        p.setFallDistance(0);
        p.setVelocity(new Vector(0, 0, 0));
        p.teleport(d.spawn(p.getUniqueId()));
        plugin.modes().applyKit(p, d.mode);
    }

    private void startRound(Duel d) {
        d.state = Duel.State.COUNTDOWN;
        d.dead.clear();
        d.dead.addAll(d.left);
        for (UUID id : d.all) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !d.left.contains(id)) prepare(p, d);
        }
        final int secs = Math.max(1, plugin.getConfig().getInt("duel.countdown-seconds", 3));
        new BukkitRunnable() {
            int t = secs;

            @Override
            public void run() {
                if (d.state == Duel.State.ENDED) { cancel(); return; }
                if (t <= 0) {
                    d.state = Duel.State.ACTIVE;
                    for (UUID id : d.all) {
                        Player p = Bukkit.getPlayer(id);
                        if (p == null) continue;
                        p.showTitle(title(plugin.messages().raw("fight-title"), 5, 20, 10));
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                    }
                    cancel();
                    return;
                }
                for (UUID id : d.all) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    p.showTitle(title(plugin.messages().raw("countdown-title", "time", String.valueOf(t)), 0, 25, 5));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                t--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private Title title(Component main, int inTicks, int stayTicks, int outTicks) {
        return Title.title(main, Component.empty(),
                Title.Times.times(Duration.ofMillis(inTicks * 50L), Duration.ofMillis(stayTicks * 50L),
                        Duration.ofMillis(outTicks * 50L)));
    }

    // ---------------------------------------------------------------- pontos

    /** Chamado quando um golpe mataria o jogador (a morte e cancelada, sem tela de respawn). */
    public void onFatal(Duel d, Player victim) {
        if (d.state != Duel.State.ACTIVE) return;
        UUID id = victim.getUniqueId();
        if (d.dead.contains(id)) return;
        d.dead.add(id);

        AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
        victim.setHealth(max != null ? max.getValue() : 20.0);
        victim.setFireTicks(0);
        victim.setGameMode(GameMode.SPECTATOR); // eliminado: assiste ate o fim do round
        broadcast(d, "player-eliminated", "player", victim.getName());

        int team = d.teamOf(id);
        if (d.teamWiped(team)) roundWin(d, team == 1 ? 2 : 1);
    }

    private void roundWin(Duel d, int winTeam) {
        if (d.state == Duel.State.ENDED || d.state == Duel.State.ROUND_END) return;
        d.state = Duel.State.ROUND_END;
        d.addScore(winTeam);

        String winners = names(d, winTeam);
        for (UUID id : d.all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.messages().send(p, "point-scored", "player", winners,
                    "score1", String.valueOf(d.score1), "score2", String.valueOf(d.score2));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }

        int target = plugin.getConfig().getInt("duel.rounds-to-win", 3);
        if (d.score(winTeam) >= target) {
            finish(d, winTeam);
        } else {
            long delay = plugin.getConfig().getLong("duel.round-delay-ticks", 40L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (d.state == Duel.State.ROUND_END) startRound(d);
            }, delay);
        }
    }

    // ------------------------------------------------------------------ fim

    private void finish(Duel d, int winTeam) {
        if (d.state == Duel.State.ENDED) return;
        d.state = Duel.State.ENDED;

        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        for (UUID id : d.team(winTeam)) (d.left.contains(id) ? losers : winners).add(id);
        for (UUID id : d.team(winTeam == 1 ? 2 : 1)) losers.add(id);

        for (UUID id : winners) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(plugin.messages().get("duel-won"));
        }
        for (UUID id : losers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(plugin.messages().get("duel-lost"));
        }

        if (d.ranked) {
            var cfg = plugin.getConfig();
            String mid = d.mode.id();
            int win = cfg.getInt("ranked.win-points", 25);
            int loss = cfg.getInt("ranked.loss-points", 20);
            for (UUID id : winners) {
                int old = plugin.data().points(id, mid);
                int now = Math.min(plugin.ranks().cap(), old + win);
                plugin.data().setResult(id, mid, true, now);
                Player p = Bukkit.getPlayer(id);
                if (p != null) rankMsg(p, d, "+" + (now - old), now);
            }
            for (UUID id : losers) {
                int old = plugin.data().points(id, mid);
                int now = Math.max(0, old - loss);
                plugin.data().setResult(id, mid, false, now);
                Player p = Bukkit.getPlayer(id);
                if (p != null) rankMsg(p, d, "-" + (old - now), now);
            }
        }

        long delay = plugin.getConfig().getLong("duel.end-delay-ticks", 60L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(d), delay);
    }

    private void rankMsg(Player p, Duel d, String delta, int points) {
        plugin.messages().send(p, "ranked-change", "mode", d.mode.displayName(),
                "rank", plugin.ranks().colored(points), "delta", delta, "points", String.valueOf(points));
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        Duel d = byPlayer.get(id);
        if (d == null || d.state == Duel.State.ENDED) return;

        d.left.add(id);
        d.dead.add(id);
        for (UUID other : d.all) {
            if (other.equals(id)) continue;
            Player o = Bukkit.getPlayer(other);
            if (o != null) plugin.messages().send(o, "opponent-quit", "player", p.getName());
        }
        int team = d.teamOf(id);
        int enemy = team == 1 ? 2 : 1;
        if (d.teamGone(team)) {
            finish(d, enemy);              // time inteiro saiu: derrota no duelo
        } else if (d.state == Duel.State.ACTIVE && d.teamWiped(team)) {
            roundWin(d, enemy);            // so restavam eliminados: o round acaba
        }
    }

    private void cleanup(Duel d) {
        if (d.cleaned) return;
        d.cleaned = true;
        d.state = Duel.State.ENDED;
        for (UUID id : d.all) {
            byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) plugin.lobby().sendToLobby(p);
        }
        active.remove(d);
        plugin.arenas().cleanup(d);
    }

    public void shutdown() {
        for (Duel d : new ArrayList<>(active)) cleanup(d);
    }
}
