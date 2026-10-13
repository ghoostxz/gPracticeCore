package br.gcelestial.practicecore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fila por modo + tipo (normal/ranqueada) + formato (1v1..4v4).
 * Cada entrada e um GRUPO: um jogador solo ou uma party inteira (que sempre fica no mesmo time).
 * Excecao: 1v1 com party de 2 = os dois membros lutam entre si.
 */
public final class QueueManager {
    public static final class Entry {
        public final List<UUID> members;
        public final Mode mode;
        public final boolean ranked;
        public final int size;
        public final long since;

        Entry(List<UUID> members, Mode mode, boolean ranked, int size) {
            this.members = members;
            this.mode = mode;
            this.ranked = ranked;
            this.size = size;
            this.since = System.currentTimeMillis();
        }

        public int weight() { return members.size(); }
    }

    private final GCelestialPracticeCore plugin;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, Entry> byPlayer = new HashMap<>();

    public QueueManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public boolean isQueued(Player p) { return byPlayer.containsKey(p.getUniqueId()); }

    public int count(Mode m, boolean ranked, int size) {
        int n = 0;
        for (Entry e : entries) if (e.mode.id().equals(m.id()) && e.ranked == ranked && e.size == size) n += e.weight();
        return n;
    }

    public int count(Mode m, boolean ranked) {
        int n = 0;
        for (Entry e : entries) if (e.mode.id().equals(m.id()) && e.ranked == ranked) n += e.weight();
        return n;
    }

    // ------------------------------------------------------------ entrar/sair

    /** Jogadores na fila (todos os modos) do tipo normal ou ranqueada. */
    public int countAll(boolean ranked) {
        int n = 0;
        for (Entry e : entries) if (e.ranked == ranked) n += e.weight();
        return n;
    }

    public void join(Player p, Mode m, boolean ranked, int size) {
        Messages msg = plugin.messages();
        Party party = plugin.parties().get(p);
        if (ranked && party != null && !plugin.getConfig().getBoolean("party.allow-ranked", false)) {
            msg.send(p, "party-ranked-denied");
            return;
        }
        List<Player> group = new ArrayList<>();
        if (party != null && party.size() > 1) {
            if (!party.isLeader(p.getUniqueId())) { msg.send(p, "party-leader-only"); return; }
            for (UUID id : party.members()) {
                Player mp = Bukkit.getPlayer(id);
                if (mp != null) group.add(mp);
            }
        } else {
            group.add(p);
        }

        if (group.size() > 1) {
            int maxSolo = plugin.getConfig().getInt("party.max-solo-party", 2);
            if (size == 1 && group.size() > maxSolo) {
                msg.send(p, "party-too-big-solo", "max", String.valueOf(maxSolo));
                return;
            }
            if (size > 1 && group.size() > size) {
                msg.send(p, "party-too-big", "size", String.valueOf(size));
                return;
            }
            if (size == 1 && ranked) { msg.send(p, "party-ranked-solo-denied"); return; }
        }
        for (Player g : group) {
            if (plugin.duels().isInDuel(g)) {
                if (g.equals(p)) msg.send(p, "in-duel");
                else msg.send(p, "party-member-busy", "player", g.getName());
                return;
            }
            if (isQueued(g)) { msg.send(p, "already-queued"); return; }
        }
        if (!plugin.modes().hasKit(m)) { msg.send(p, "no-kit", "mode", m.displayName()); return; }
        if (!plugin.arenas().hasArena(m.id())) { msg.send(p, "no-arena", "mode", m.displayName()); return; }

        List<UUID> ids = new ArrayList<>();
        for (Player g : group) ids.add(g.getUniqueId());
        Entry e = new Entry(ids, m, ranked, size);
        entries.add(e);
        for (Player g : group) {
            byPlayer.put(g.getUniqueId(), e);
            plugin.lobby().updateDynamic(g); // mostra "Sair da fila" na hotbar
            msg.send(g, "queue-join", "type", ranked ? "Ranqueada" : "Normal",
                    "size", size + "v" + size, "mode", m.displayName());
        }
    }

    /** Sai da fila (a party inteira sai junto). */
    public void leave(Player p, boolean silent) {
        Entry e = byPlayer.get(p.getUniqueId());
        if (e == null) return;
        detach(e);
        if (!silent) {
            for (UUID id : e.members) {
                Player m = Bukkit.getPlayer(id);
                if (m != null) plugin.messages().send(m, "queue-leave");
            }
        }
    }

    private void detach(Entry e) {
        entries.remove(e);
        for (UUID id : e.members) {
            byPlayer.remove(id);
            Player m = Bukkit.getPlayer(id);
            if (m == null || !m.isOnline()) continue;
            plugin.lobby().updateDynamic(m); // remove "Sair da fila" da hotbar
        }
    }

    // --------------------------------------------------------- matchmaking

    private int allowed(Entry e) {
        var cfg = plugin.getConfig();
        int base = cfg.getInt("ranked.max-point-diff", 100);
        int step = cfg.getInt("ranked.expand-step", 50);
        int every = Math.max(1, cfg.getInt("ranked.expand-every-seconds", 10));
        long waited = (System.currentTimeMillis() - e.since) / 1000L;
        return base + step * (int) (waited / every);
    }

    private int pts(Entry e, UUID id) {
        return plugin.data().points(id, e.mode.id());
    }

    private double avg(Entry e) {
        double s = 0;
        for (UUID id : e.members) s += pts(e, id);
        return s / e.members.size();
    }

    private void tick() {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : entries) {
            groups.computeIfAbsent(e.mode.id() + ":" + e.ranked + ":" + e.size, k -> new ArrayList<>()).add(e);
        }
        for (List<Entry> list : groups.values()) {
            Entry first = list.get(0);
            int size = first.size;
            int need = size * 2;

            // 1v1 com party de 2: os dois membros lutam entre si
            if (size == 1) {
                for (Entry e : new ArrayList<>(list)) {
                    if (e.weight() == 2) {
                        list.remove(e);
                        matchDuo(e);
                    }
                }
            }

            boolean found;
            do {
                found = false;
                int total = 0;
                for (Entry e : list) total += e.weight();
                if (total < need) break;
                for (Entry anchor : new ArrayList<>(list)) {
                    List<Entry> sel = find(list, anchor, size, first.ranked);
                    if (sel != null) {
                        list.removeAll(sel);
                        match(sel, size, first.ranked);
                        found = true;
                        break;
                    }
                }
            } while (found && !list.isEmpty());
        }
    }

    private List<Entry> find(List<Entry> list, Entry anchor, int size, boolean ranked) {
        List<Entry> cands = new ArrayList<>();
        for (Entry e : list) if (e != anchor && e.weight() <= size) cands.add(e);
        if (ranked) {
            double ap = avg(anchor);
            cands.sort(Comparator.comparingDouble(e -> Math.abs(avg(e) - ap)));
        }
        if (cands.size() > 16) cands = new ArrayList<>(cands.subList(0, 16));
        List<Entry> chosen = new ArrayList<>();
        chosen.add(anchor);
        return dfs(cands, 0, chosen, anchor.weight(), size, ranked);
    }

    private List<Entry> dfs(List<Entry> cands, int idx, List<Entry> chosen, int sum, int size, boolean ranked) {
        int need = size * 2;
        if (sum == need) return valid(chosen, size, ranked) ? new ArrayList<>(chosen) : null;
        for (int i = idx; i < cands.size(); i++) {
            Entry c = cands.get(i);
            if (sum + c.weight() > need) continue;
            chosen.add(c);
            List<Entry> r = dfs(cands, i + 1, chosen, sum + c.weight(), size, ranked);
            chosen.remove(chosen.size() - 1);
            if (r != null) return r;
        }
        return null;
    }

    private boolean valid(List<Entry> chosen, int size, boolean ranked) {
        if (split(chosen, size, ranked) == null) return false;
        if (!ranked) return true;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int allowed = Integer.MAX_VALUE;
        for (Entry e : chosen) {
            allowed = Math.min(allowed, allowed(e));
            for (UUID id : e.members) {
                int p = pts(e, id);
                min = Math.min(min, p);
                max = Math.max(max, p);
            }
        }
        return max - min <= allowed;
    }

    /** Divide os grupos em dois times de exatamente 'size' jogadores (party nunca e separada). */
    private List<List<Entry>> split(List<Entry> chosen, int size, boolean ranked) {
        int n = chosen.size();
        List<List<Entry>> best = null;
        double bestDiff = Double.MAX_VALUE;
        for (int mask = 0; mask < (1 << n); mask += 2) { // bit 0 = 0: o primeiro grupo fica no time A
            int sumA = 0;
            for (int i = 0; i < n; i++) if (((mask >> i) & 1) == 0) sumA += chosen.get(i).weight();
            if (sumA != size) continue;
            List<Entry> a = new ArrayList<>();
            List<Entry> b = new ArrayList<>();
            double totA = 0;
            double totB = 0;
            for (int i = 0; i < n; i++) {
                Entry e = chosen.get(i);
                double t = 0;
                if (ranked) for (UUID id : e.members) t += pts(e, id);
                if (((mask >> i) & 1) == 0) { a.add(e); totA += t; } else { b.add(e); totB += t; }
            }
            double diff = Math.abs(totA - totB);
            if (best == null || diff < bestDiff) {
                best = List.of(a, b);
                bestDiff = diff;
            }
        }
        return best;
    }

    private void matchDuo(Entry e) {
        Player a = Bukkit.getPlayer(e.members.get(0));
        Player b = Bukkit.getPlayer(e.members.get(1));
        if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
            detach(e);
            return;
        }
        plugin.duels().start(List.of(a), List.of(b), e.mode, false, 1);
    }

    private void match(List<Entry> chosen, int size, boolean ranked) {
        List<List<Entry>> teams = split(chosen, size, ranked);
        if (teams == null) return;
        List<List<Player>> out = new ArrayList<>();
        for (List<Entry> team : teams) {
            List<Player> players = new ArrayList<>();
            for (Entry e : team) {
                for (UUID id : e.members) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !p.isOnline()) {
                        detach(e); // os demais continuam na fila e sao reavaliados no proximo tick
                        return;
                    }
                    players.add(p);
                }
            }
            out.add(players);
        }
        Entry f = chosen.get(0);
        plugin.duels().start(out.get(0), out.get(1), f.mode, f.ranked, f.size);
    }
}
