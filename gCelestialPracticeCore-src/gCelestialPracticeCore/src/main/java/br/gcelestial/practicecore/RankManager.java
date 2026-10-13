package br.gcelestial.practicecore;

public final class RankManager {
    public static final String[] TIERS = {"LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1"};
    private static final String[] COLORS = {"&7", "&f", "&a", "&2", "&e", "&6", "&c", "&4", "&d", "&b"};

    private final GCelestialPracticeCore plugin;

    public RankManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    private int per() {
        return Math.max(1, plugin.getConfig().getInt("ranked.points-per-rank", 100));
    }

    /** Pontuacao maxima (topo do HT1 = ultimo ponto do ultimo rank). */
    public int cap() {
        return per() * TIERS.length + per() - 1;
    }

    public int index(int points) {
        return Math.min(TIERS.length - 1, Math.max(0, points / per()));
    }

    public String rank(int points) {
        return TIERS[index(points)];
    }

    public String colored(int points) {
        int i = index(points);
        return COLORS[i] + TIERS[i];
    }
}
