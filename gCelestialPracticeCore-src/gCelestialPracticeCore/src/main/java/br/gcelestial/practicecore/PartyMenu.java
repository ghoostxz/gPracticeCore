package br.gcelestial.practicecore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PartyMenu {
    public static final class PartyHolder extends MenuManager.GuiHolder {
    }

    private final GCelestialPracticeCore plugin;
    private final NamespacedKey key;

    public PartyMenu(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "gcpc_party");
    }

    // ------------------------------------------------------------- helpers

    private Inventory create(String title) {
        PartyHolder holder = new PartyHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, Text.c(title));
        holder.inventory = inv;
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = f.getItemMeta();
        fm.displayName(net.kyori.adventure.text.Component.space());
        f.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
        return inv;
    }

    private ItemStack item(Material mat, String name, List<String> lore, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(name));
        m.lore(Text.c(lore));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (action != null) m.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack head(OfflinePlayer op, String name, List<String> lore, String action) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(op);
        m.displayName(Text.c(name));
        m.lore(Text.c(lore));
        if (action != null) m.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    public String action(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    // --------------------------------------------------------------- menus

    public void open(Player p) {
        PartyManager pm = plugin.parties();
        Party party = pm.get(p);
        String title = plugin.getConfig().getString("menu.party-title", "&8Party");
        Inventory inv = create(title);

        if (party == null) {
            inv.setItem(11, item(Material.EMERALD, "&aCriar Party",
                    List.of("&7Crie uma party e convide seus amigos.", "", "&eClique para criar"), "create"));
            inv.setItem(15, item(Material.COMPASS, "&bParties Publicas",
                    List.of("&7Entre na party aberta de alguem.",
                            "&7Disponiveis: &f" + pm.openParties().size(), "", "&eClique para ver"), "public-list"));
            p.openInventory(inv);
            return;
        }

        boolean leader = party.isLeader(p.getUniqueId());
        inv.setItem(4, item(Material.NAME_TAG, "&6Party &7- &f" + party.size() + "/" + pm.max(),
                List.of("&7Privacidade: " + pm.privacy(party)), null));

        int i = 0;
        for (UUID id : party.members()) {
            if (i >= 9) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean isLeader = party.isLeader(id);
            String name = (isLeader ? "&6★ &f" : "&f") + (op.getName() != null ? op.getName() : "?");
            List<String> lore = new ArrayList<>();
            lore.add(isLeader ? "&6Lider da party" : "&7Membro");
            String act = null;
            if (leader && !id.equals(p.getUniqueId())) {
                lore.add("");
                lore.add("&cClique para remover da party");
                act = "kick:" + id;
            }
            inv.setItem(9 + i, head(op, name, lore, act));
            i++;
        }

        if (leader) {
            inv.setItem(28, item(Material.WRITABLE_BOOK, "&aConvidar jogador",
                    List.of("&7Escolha um jogador online.", "", "&eClique para abrir"), "invite-menu"));
        }
        inv.setItem(30, item(party.isOpen() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&7Privacidade: " + pm.privacy(party),
                leader ? List.of("&7Publica: qualquer um entra.", "&7Privada: so por convite.", "", "&eClique para alternar")
                        : List.of("&7Apenas o lider pode alterar."),
                leader ? "toggle" : null));
        inv.setItem(32, item(Material.RED_DYE, "&cSair da party",
                List.of(leader ? "&7A lideranca passa para outro membro." : "&7Voce deixa a party."), "leave"));
        if (leader) {
            inv.setItem(34, item(Material.BARRIER, "&4Desfazer party", List.of("&7Remove todos os membros."), "disband"));
        }
        p.openInventory(inv);
    }

    public void openInvite(Player p) {
        Inventory inv = create("&8Convidar jogador");
        int i = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (i >= 27) break;
            if (t.equals(p) || plugin.parties().get(t) != null || plugin.duels().isInDuel(t)) continue;
            inv.setItem(i++, head(t, "&f" + t.getName(), List.of("&eClique para convidar"), "invite:" + t.getUniqueId()));
        }
        inv.setItem(31, item(Material.ARROW, "&cVoltar", List.of(), "back"));
        p.openInventory(inv);
    }

    public void openPublic(Player p) {
        PartyManager pm = plugin.parties();
        Inventory inv = create("&8Parties Publicas");
        int i = 0;
        for (Party party : pm.openParties()) {
            if (i >= 27) break;
            OfflinePlayer lead = Bukkit.getOfflinePlayer(party.leader());
            List<String> lore = new ArrayList<>();
            lore.add("&7Membros: &f" + party.size() + "/" + pm.max());
            lore.add("");
            lore.add("&eClique para entrar");
            inv.setItem(i++, head(lead, "&fParty de " + (lead.getName() != null ? lead.getName() : "?"),
                    lore, "join:" + party.leader()));
        }
        inv.setItem(31, item(Material.ARROW, "&cVoltar", List.of(), "back"));
        p.openInventory(inv);
    }

    // -------------------------------------------------------------- cliques

    public void handle(Player p, String action) {
        PartyManager pm = plugin.parties();
        try {
            if (action.equals("create")) {
                pm.create(p);
                open(p);
            } else if (action.equals("invite-menu")) {
                openInvite(p);
            } else if (action.equals("public-list")) {
                openPublic(p);
            } else if (action.equals("toggle")) {
                Party party = pm.get(p);
                if (party != null) pm.setOpen(p, !party.isOpen());
                open(p);
            } else if (action.equals("leave")) {
                pm.leave(p, false);
                open(p);
            } else if (action.equals("disband")) {
                pm.disband(p);
                open(p);
            } else if (action.equals("back")) {
                open(p);
            } else if (action.startsWith("kick:")) {
                pm.kick(p, UUID.fromString(action.substring(5)));
                open(p);
            } else if (action.startsWith("invite:")) {
                Player t = Bukkit.getPlayer(UUID.fromString(action.substring(7)));
                if (t != null) pm.invite(p, t);
                open(p);
            } else if (action.startsWith("join:")) {
                pm.join(p, UUID.fromString(action.substring(5)));
                open(p);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
