package br.gcelestial.practicecore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PracticeListener implements Listener {
    private final GCelestialPracticeCore plugin;

    public PracticeListener(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    /** Admin em criativo pode mexer livremente no lobby. */
    private boolean canBuild(Player p) {
        return p.hasPermission("gcelestial.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.lobby().sendToLobby(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.queue().leave(p, true);
        plugin.duels().handleQuit(p);
        plugin.parties().leave(p, true);
        plugin.challenges().purge(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location s = plugin.data().getSpawn();
        if (s != null) e.setRespawnLocation(s);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // Seguranca: a morte normalmente e cancelada antes (ver onDamage).
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Duel d = plugin.duels().get(p);
        if (d != null) {
            if (d.state != Duel.State.ACTIVE) e.setCancelled(true);
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        String id = plugin.lobby().itemId(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        switch (id) {
            case "unranked" -> plugin.menus().open(p, false);
            case "ranked" -> plugin.menus().open(p, true);
            case "party" -> plugin.partyMenu().open(p);
            case "kit-editor" -> plugin.layoutMenu().openModes(p);
            case "party-disband" -> plugin.parties().disband(p);
            case "party-leave" -> plugin.parties().leave(p, false);
            case "leave-queue" -> plugin.queue().leave(p, false);
            default -> { }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        var top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuManager.MenuHolder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                Mode m = plugin.modes().get(plugin.menus().modeId(e.getCurrentItem()));
                if (m != null) {
                    boolean ranked = h.ranked();
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.menus().openSizes(p, m, ranked));
                }
            }
            return;
        }
        if (top.getHolder() instanceof MenuManager.SizeHolder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                String v = plugin.menus().sizeValue(e.getCurrentItem());
                if (v == null) return;
                if (v.equals("back")) {
                    boolean ranked = h.ranked();
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.menus().open(p, ranked));
                    return;
                }
                try {
                    int size = Integer.parseInt(v);
                    p.closeInventory();
                    plugin.queue().join(p, h.mode(), h.ranked(), size);
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }
        if (top.getHolder() instanceof ChallengeManager.ChallengeHolder ch) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                String id = plugin.menus().modeId(e.getCurrentItem());
                if (id != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.closeInventory();
                        plugin.challenges().send(p, ch.target(), id);
                    });
                }
            }
            return;
        }
        if (top.getHolder() instanceof LayoutMenu.EditorHolder eh) {
            e.setCancelled(true); // nenhum item real e movido: o editor troca posicoes por conta propria
            if (e.getRawSlot() >= 0 && e.getRawSlot() < top.getSize()) {
                plugin.layoutMenu().clickEditor(p, eh, e.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof LayoutMenu.ModeHolder) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                String id = plugin.layoutMenu().modeAction(e.getCurrentItem());
                if (id != null) Bukkit.getScheduler().runTask(plugin, () -> plugin.layoutMenu().openEditor(p, id));
            }
            return;
        }
        if (top.getHolder() instanceof PartyMenu.PartyHolder) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                String act = plugin.partyMenu().action(e.getCurrentItem());
                if (act != null) Bukkit.getScheduler().runTask(plugin, () -> plugin.partyMenu().handle(p, act));
            }
            return;
        }
        if (plugin.duels().get(p) == null && !canBuild(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && e.getInventory().getHolder() instanceof LayoutMenu.EditorHolder h) {
            plugin.layoutMenu().onClose(p, h); // fechar o menu salva
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof MenuManager.GuiHolder
                || (plugin.duels().get(p) == null && !canBuild(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (plugin.duels().get(p) == null && !canBuild(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && plugin.duels().get(p) == null && !canBuild(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.duels().get(p) == null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Duel d = plugin.duels().get(p);
        if (d != null) {
            if (d.state != Duel.State.ACTIVE) e.setCancelled(true);
            else d.placed.add(e.getBlockPlaced().getLocation());
            return;
        }
        if (!canBuild(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Duel d = plugin.duels().get(p);
        if (d != null) {
            if (d.state != Duel.State.ACTIVE) e.setCancelled(true);
            return;
        }
        if (!canBuild(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Duel d = plugin.duels().get(e.getPlayer());
        if (d == null || d.state != Duel.State.COUNTDOWN) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t != null && (f.getX() != t.getX() || f.getZ() != t.getZ())) {
            t.setX(f.getX());
            t.setZ(f.getZ());
            e.setTo(t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Duel d = plugin.duels().get(p);
        if (d == null) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) plugin.lobby().teleportSpawn(p);
            return;
        }
        if (d.state != Duel.State.ACTIVE) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) p.teleport(d.spawn(p.getUniqueId()));
            return;
        }
        if (d.isDead(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ee) {
            org.bukkit.entity.Entity src = ee.getDamager();
            if (src instanceof org.bukkit.entity.Projectile proj
                    && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                src = shooter;
            }
            if (src instanceof Player dmg && !dmg.equals(p)
                    && d.teamOf(dmg.getUniqueId()) == d.teamOf(p.getUniqueId())) {
                e.setCancelled(true); // sem fogo amigo
                return;
            }
        }
        if (p.getHealth() - e.getFinalDamage() <= 0 && !hasTotem(p)) {
            e.setCancelled(true);
            plugin.duels().onFatal(d, p);
        }
    }

    private boolean hasTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }
}
