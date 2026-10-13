package br.gcelestial.practicecore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * plugins/gCelestialPracticeCore/arenas/<modo>/*.yml  +  schematic (.schem) na mesma pasta.
 * Cada .yml define uma arena (varias arenas por modo = sorteio).
 */
public final class ArenaManager {
    public record Pasted(EditSession session, World world, int ox, int oy, int oz) {}

    private final GCelestialPracticeCore plugin;
    private final Map<String, List<Arena>> arenas = new HashMap<>();
    private final Map<File, Clipboard> cache = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();

    public ArenaManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        cache.clear();
        File root = new File(plugin.getDataFolder(), "arenas");
        root.mkdirs();
        for (Mode mode : plugin.modes().all()) {
            File dir = new File(root, mode.id());
            dir.mkdirs();
            File[] ymls = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (ymls == null || ymls.length == 0) {
                writeDefault(new File(dir, "arena.yml"));
                ymls = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
            }
            List<Arena> list = new ArrayList<>();
            if (ymls != null) {
                for (File y : ymls) {
                    Arena a = parse(mode, dir, y);
                    if (a != null) list.add(a);
                }
            }
            arenas.put(mode.id(), list);
            plugin.getLogger().info("Modo " + mode.id() + ": " + list.size() + " arena(s) carregada(s).");
        }
    }

    private void writeDefault(File f) {
        String s = """
                # Nome da schematic (.schem) que esta NESTA pasta
                schematic: arena.schem
                # Posicoes RELATIVAS a origem da schematic (o ponto onde voce estava ao fazer //copy).
                # Exemplo: x: 0.5 y: 1.0 z: 10.5 = 0.5 blocos em X, 1 acima, 10.5 em Z a partir da origem.
                player1:
                  x: 0.5
                  y: 1.0
                  z: 10.5
                  yaw: 180.0
                  pitch: 0.0
                player2:
                  x: 0.5
                  y: 1.0
                  z: -10.5
                  yaw: 0.0
                  pitch: 0.0
                """;
        try {
            Files.writeString(f.toPath(), s);
        } catch (Exception e) {
            plugin.getLogger().warning("Nao foi possivel criar " + f + ": " + e.getMessage());
        }
    }

    private Arena parse(Mode mode, File dir, File yml) {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(yml);
        File schem = new File(dir, c.getString("schematic", "arena.schem"));
        if (!schem.exists()) {
            plugin.getLogger().warning("[" + mode.id() + "] schematic nao encontrada: " + schem.getName()
                    + " (arena " + yml.getName() + " ignorada)");
            return null;
        }
        Arena.Spawn p1 = spawn(c.getConfigurationSection("player1"));
        Arena.Spawn p2 = spawn(c.getConfigurationSection("player2"));
        if (p1 == null || p2 == null) {
            plugin.getLogger().warning("[" + mode.id() + "] player1/player2 ausentes em " + yml.getName());
            return null;
        }
        return new Arena(mode.id(), yml.getName(), schem, p1, p2);
    }

    private Arena.Spawn spawn(ConfigurationSection s) {
        if (s == null) return null;
        return new Arena.Spawn(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public List<Arena> of(String modeId) {
        return arenas.getOrDefault(modeId, List.of());
    }

    public boolean hasArena(String modeId) {
        return !of(modeId).isEmpty();
    }

    public int allocateSlot() {
        int i = 0;
        while (usedSlots.contains(i)) i++;
        usedSlots.add(i);
        return i;
    }

    public void freeSlot(int slot) {
        usedSlots.remove(slot);
    }

    private World world() {
        String name = plugin.getConfig().getString("duel.world", "practice_arenas");
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(new ChunkGenerator() {}).createWorld(); // mundo vazio (void)
        }
        return w;
    }

    private Clipboard clipboard(Arena a) throws Exception {
        Clipboard cb = cache.get(a.schematic());
        if (cb != null) return cb;
        ClipboardFormat format = ClipboardFormats.findByFile(a.schematic());
        if (format == null) throw new IllegalStateException("Formato de schematic invalido: " + a.schematic().getName());
        try (ClipboardReader reader = format.getReader(new FileInputStream(a.schematic()))) {
            cb = reader.read();
        }
        cache.put(a.schematic(), cb);
        return cb;
    }

    public Pasted paste(Arena arena, int slot) throws Exception {
        World w = world();
        int spacing = plugin.getConfig().getInt("duel.slot-spacing", 1000);
        int ox = slot * spacing;
        int oy = plugin.getConfig().getInt("duel.paste-y", 100);
        int oz = 0;
        Clipboard cb = clipboard(arena);
        com.sk89q.worldedit.world.World ww = BukkitAdapter.adapt(w);
        EditSession es = WorldEdit.getInstance().newEditSession(ww);
        try {
            Operation op = new ClipboardHolder(cb).createPaste(es)
                    .to(BlockVector3.at(ox, oy, oz)).ignoreAirBlocks(false).build();
            Operations.complete(op);
        } finally {
            es.close();
        }
        return new Pasted(es, w, ox, oy, oz);
    }

    public Location toLocation(Pasted p, Arena.Spawn s) {
        return new Location(p.world(), p.ox() + s.x(), p.oy() + s.y(), p.oz() + s.z(), s.yaw(), s.pitch());
    }

    /** Remove blocos colocados, entidades (cristais, itens...), desfaz a colagem e libera o slot. */
    public void cleanup(Duel d) {
        try {
            for (Location l : d.placed) l.getBlock().setType(Material.AIR, false);
            Pasted p = d.pasted;
            double r = plugin.getConfig().getInt("duel.slot-spacing", 1000) / 2.0 - 10;
            Location c = new Location(p.world(), p.ox(), p.oy(), p.oz());
            for (Entity e : p.world().getNearbyEntities(c, r, 320, r)) {
                if (!(e instanceof Player)) e.remove();
            }
            try (EditSession undo = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(p.world()))) {
                p.session().undo(undo);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Erro ao limpar arena: " + ex.getMessage());
        } finally {
            freeSlot(d.pasted.ox() / Math.max(1, plugin.getConfig().getInt("duel.slot-spacing", 1000)));
        }
    }
}
