package br.gcelestial.practicecore;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Duel {
    public enum State { COUNTDOWN, ACTIVE, ROUND_END, ENDED }

    public final List<UUID> team1;
    public final List<UUID> team2;
    public final List<UUID> all = new ArrayList<>();
    public final Mode mode;
    public final Arena arena;
    public final boolean ranked;
    public final int size;
    public final ArenaManager.Pasted pasted;
    public final Location spawn1;
    public final Location spawn2;
    public final List<Location> placed = new ArrayList<>();
    /** Eliminados no round atual (inclui quem saiu). */
    public final Set<UUID> dead = new HashSet<>();
    /** Quem desconectou durante o duelo. */
    public final Set<UUID> left = new HashSet<>();
    public int score1;
    public int score2;
    public State state = State.COUNTDOWN;
    public boolean cleaned;

    public Duel(List<UUID> team1, List<UUID> team2, Mode mode, Arena arena, boolean ranked, int size,
                ArenaManager.Pasted pasted, Location spawn1, Location spawn2) {
        this.team1 = team1;
        this.team2 = team2;
        this.all.addAll(team1);
        this.all.addAll(team2);
        this.mode = mode;
        this.arena = arena;
        this.ranked = ranked;
        this.size = size;
        this.pasted = pasted;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
    }

    public int teamOf(UUID id) { return team1.contains(id) ? 1 : (team2.contains(id) ? 2 : 0); }

    public List<UUID> team(int t) { return t == 1 ? team1 : team2; }

    public Location spawn(UUID id) { return teamOf(id) == 1 ? spawn1 : spawn2; }

    public boolean isDead(UUID id) { return dead.contains(id); }

    public boolean teamWiped(int t) {
        for (UUID id : team(t)) if (!dead.contains(id)) return false;
        return true;
    }

    public boolean teamGone(int t) {
        for (UUID id : team(t)) if (!left.contains(id)) return false;
        return true;
    }

    public int score(int t) { return t == 1 ? score1 : score2; }

    public void addScore(int t) { if (t == 1) score1++; else score2++; }
}
