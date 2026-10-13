package br.gcelestial.practicecore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** data.yml: spawn do practice + pontos/vitorias/derrotas por modo. */
public final class DataStore {
    private final GCelestialPracticeCore plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public DataStore(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Falha ao salvar data.yml: " + e.getMessage());
        }
    }

    public Location getSpawn() {
        ConfigurationSection s = cfg.getConfigurationSection("spawn");
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void setSpawn(Location l) {
        cfg.set("spawn.world", l.getWorld().getName());
        cfg.set("spawn.x", l.getX());
        cfg.set("spawn.y", l.getY());
        cfg.set("spawn.z", l.getZ());
        cfg.set("spawn.yaw", l.getYaw());
        cfg.set("spawn.pitch", l.getPitch());
        save();
    }

    private String path(UUID id, String mode, String field) {
        return "players." + id + "." + mode + "." + field;
    }

    public int points(UUID id, String mode) {
        return cfg.getInt(path(id, mode, "points"), plugin.getConfig().getInt("ranked.start-points", 0));
    }

    /** Layout salvo (src[slot] = slot original do kit) ou null se nao existe / o kit mudou. */
    public int[] getLayout(UUID id, String mode, int hash) {
        String b = "layouts." + id + "." + mode;
        if (!cfg.contains(b + ".hash") || cfg.getInt(b + ".hash") != hash) return null;
        java.util.List<Integer> l = cfg.getIntegerList(b + ".slots");
        if (l.size() != 36) return null;
        int[] a = new int[36];
        for (int i = 0; i < 36; i++) a[i] = l.get(i);
        return a;
    }

    public void setLayout(UUID id, String mode, int[] src, int hash) {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int v : src) l.add(v);
        cfg.set("layouts." + id + "." + mode + ".hash", hash);
        cfg.set("layouts." + id + "." + mode + ".slots", l);
        save();
    }

    public void clearLayout(UUID id, String mode) {
        cfg.set("layouts." + id + "." + mode, null);
        save();
    }

    public int wins(UUID id, String mode) { return cfg.getInt(path(id, mode, "wins"), 0); }

    public int losses(UUID id, String mode) { return cfg.getInt(path(id, mode, "losses"), 0); }

    public void setResult(UUID id, String mode, boolean win, int newPoints) {
        cfg.set(path(id, mode, "points"), newPoints);
        String f = win ? "wins" : "losses";
        cfg.set(path(id, mode, f), cfg.getInt(path(id, mode, f), 0) + 1);
        save();
    }
}
