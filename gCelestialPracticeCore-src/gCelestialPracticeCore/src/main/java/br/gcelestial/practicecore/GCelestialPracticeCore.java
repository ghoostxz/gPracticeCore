package br.gcelestial.practicecore;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GCelestialPracticeCore extends JavaPlugin {
    private Messages messages;
    private DataStore data;
    private RankManager ranks;
    private ModeManager modes;
    private ArenaManager arenas;
    private LobbyManager lobby;
    private QueueManager queue;
    private DuelManager duels;
    private MenuManager menus;
    private PartyManager parties;
    private PartyMenu partyMenu;
    private LayoutMenu layoutMenu;
    private ChallengeManager challenges;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        data = new DataStore(this);
        ranks = new RankManager(this);
        modes = new ModeManager(this);
        arenas = new ArenaManager(this);
        lobby = new LobbyManager(this);
        queue = new QueueManager(this);
        duels = new DuelManager(this);
        menus = new MenuManager(this);
        parties = new PartyManager(this);
        partyMenu = new PartyMenu(this);
        layoutMenu = new LayoutMenu(this);
        challenges = new ChallengeManager(this);

        modes.load();
        arenas.load();
        lobby.validateSlots();

        getServer().getPluginManager().registerEvents(new PracticeListener(this), this);
        PracticeCommand cmd = new PracticeCommand(this);
        for (String name : new String[]{"gcpc", "party", "spawn", "setspawn", "lobby", "discord", "site", "loja", "duelo", "dueloaceitar"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        queue.start();
        menus.startRefresh();
        for (Player p : Bukkit.getOnlinePlayers()) lobby.sendToLobby(p);
        getLogger().info("gCelestialPracticeCore ativado.");
    }

    @Override
    public void onDisable() {
        if (duels != null) duels.shutdown();
    }

    /** /gcpc reload */
    public void reloadAll() {
        reloadConfig();
        modes.load();
        arenas.load();
        lobby.validateSlots();
        for (Player p : Bukkit.getOnlinePlayers()) lobby.refreshHotbar(p); // aplica novos slots/itens
    }

    /** Envia o jogador a outro servidor da rede (canal BungeeCord, aceito pelo Velocity). */
    public void connect(Player p, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    public Messages messages() { return messages; }
    public DataStore data() { return data; }
    public RankManager ranks() { return ranks; }
    public ModeManager modes() { return modes; }
    public ArenaManager arenas() { return arenas; }
    public LobbyManager lobby() { return lobby; }
    public QueueManager queue() { return queue; }
    public DuelManager duels() { return duels; }
    public MenuManager menus() { return menus; }
    public PartyManager parties() { return parties; }
    public PartyMenu partyMenu() { return partyMenu; }
    public LayoutMenu layoutMenu() { return layoutMenu; }
    public ChallengeManager challenges() { return challenges; }

    /** Le um gamemode do config (ex.: gamemode.lobby) com valor padrao se invalido. */
    public org.bukkit.GameMode gamemode(String path, org.bukkit.GameMode def) {
        try {
            return org.bukkit.GameMode.valueOf(getConfig().getString(path, def.name()).toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
