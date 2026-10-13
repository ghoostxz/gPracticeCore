package br.gcelestial.practicecore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModeManager {
    private final GCelestialPracticeCore plugin;
    private final Map<String, Mode> modes = new LinkedHashMap<>();
    private File kitsFile;
    private YamlConfiguration kits;

    public ModeManager(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        modes.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modes");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                Material icon = Material.matchMaterial(s.getString(key + ".icon", "IRON_SWORD"));
                if (icon == null) icon = Material.IRON_SWORD;
                String id = key.toLowerCase();
                modes.put(id, new Mode(id, s.getString(key + ".name", key), icon));
            }
        }
        plugin.getDataFolder().mkdirs();
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        kits = YamlConfiguration.loadConfiguration(kitsFile);
    }

    public Mode get(String id) { return id == null ? null : modes.get(id.toLowerCase()); }

    public Collection<Mode> all() { return modes.values(); }

    public List<String> ids() { return new ArrayList<>(modes.keySet()); }

    public boolean hasKit(Mode m) { return kits.isList(m.id()); }

    /** Copia o inventario inteiro (hotbar, mochila, armadura e offhand) do jogador como kit do modo. */
    public void saveKit(Mode m, Player p) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            list.add(it == null ? new ItemStack(Material.AIR) : it.clone());
        }
        kits.set(m.id(), list);
        try {
            kits.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Falha ao salvar kits.yml: " + e.getMessage());
        }
    }

    /** Itens do kit nos slots 0-35 (hotbar + mochila); armadura/offhand ficam fora do layout. */
    public ItemStack[] kitStorage(Mode m) {
        ItemStack[] out = new ItemStack[36];
        List<?> l = kits.getList(m.id());
        if (l == null) return out;
        for (int i = 0; i < 36 && i < l.size(); i++) {
            if (l.get(i) instanceof ItemStack is && is.getType() != Material.AIR) out[i] = is.clone();
        }
        return out;
    }

    /** Assinatura do kit (tipo + quantidade por slot). Se o admin mudar o kit, layouts antigos sao descartados. */
    public int kitHash(Mode m) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack it : kitStorage(m)) {
            sb.append(it == null ? "-" : it.getType().name() + "x" + it.getAmount()).append(';');
        }
        return sb.toString().hashCode();
    }

    public void applyKit(Player p, Mode m) {
        p.getInventory().clear();
        List<?> l = kits.getList(m.id());
        if (l == null) return;
        ItemStack[] arr = new ItemStack[l.size()];
        for (int i = 0; i < arr.length; i++) {
            Object o = l.get(i);
            if (o instanceof ItemStack is && is.getType() != Material.AIR) arr[i] = is.clone();
        }
        // Layout personalizado do jogador (apenas troca de posicao dos slots 0-35)
        int[] layout = plugin.data().getLayout(p.getUniqueId(), m.id(), kitHash(m));
        if (layout != null && arr.length >= 36) {
            ItemStack[] storage = new ItemStack[36];
            for (int s = 0; s < 36; s++) {
                int src = layout[s];
                if (src >= 0 && src < 36 && arr[src] != null) storage[s] = arr[src].clone();
            }
            System.arraycopy(storage, 0, arr, 0, 36);
        }
        p.getInventory().setContents(arr);
        p.updateInventory();
    }
}
