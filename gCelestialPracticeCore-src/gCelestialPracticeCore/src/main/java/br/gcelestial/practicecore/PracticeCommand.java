package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PracticeCommand implements TabExecutor {
    private static final String ADMIN = "gcelestial.admin";
    private static final List<String> PARTY_SUBS = List.of("create", "invite", "accept", "join", "kick",
            "leave", "disband", "public", "private", "info");

    private final GCelestialPracticeCore plugin;

    public PracticeCommand(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Messages msg = plugin.messages();
        String cn = cmd.getName().toLowerCase(Locale.ROOT);
        if (cn.equals("discord") || cn.equals("site") || cn.equals("loja")) return link(s, cn);
        if (cn.equals("duelo")) return duelo(s, a);
        if (cn.equals("dueloaceitar")) return dueloAceitar(s, a);
        if (cn.equals("spawn")) return spawn(s);
        if (cn.equals("setspawn")) return setSpawn(s);
        if (cn.equals("lobby")) return lobby(s);
        if (cmd.getName().equalsIgnoreCase("party")) return party(s, a);
        if (a.length == 0) { msg.send(s, "usage"); return true; }
        if (a[0].equalsIgnoreCase("party")) return party(s, Arrays.copyOfRange(a, 1, a.length));

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!s.hasPermission(ADMIN)) { msg.send(s, "no-permission"); return true; }
                plugin.reloadAll();
                msg.send(s, "reload");
            }
            case "setspawn" -> setSpawn(s);
            case "spawn" -> spawn(s);
            case "lobby" -> lobby(s);
            case "setkit", "editor", "kit" -> {
                if (!s.hasPermission(ADMIN)) { msg.send(s, "no-permission"); return true; }
                if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
                Mode m = a.length > 1 ? plugin.modes().get(a[1]) : null;
                if (m == null) {
                    msg.send(s, "unknown-mode", "modes", String.join(", ", plugin.modes().ids()));
                    return true;
                }
                plugin.modes().saveKit(m, p);
                msg.send(s, "kit-set", "mode", m.displayName());
            }
            case "stats" -> {
                if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
                for (Mode m : plugin.modes().all()) {
                    int pts = plugin.data().points(p.getUniqueId(), m.id());
                    msg.send(s, "stats-line", "mode", m.displayName(), "rank", plugin.ranks().colored(pts),
                            "points", String.valueOf(pts),
                            "wins", String.valueOf(plugin.data().wins(p.getUniqueId(), m.id())),
                            "losses", String.valueOf(plugin.data().losses(p.getUniqueId(), m.id())));
                }
            }
            case "leave" -> {
                if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
                plugin.queue().leave(p, false);
            }
            default -> msg.send(s, "usage");
        }
        return true;
    }

    /** /discord (/dc), /site, /loja: envia as linhas configuradas em links.<nome> (a linha inteira abre o link). */
    private boolean link(CommandSender s, String key) {
        var sec = plugin.getConfig().getConfigurationSection("links." + key);
        if (sec == null) { plugin.messages().send(s, "link-missing"); return true; }
        String url = sec.getString("url", "");
        boolean clickable = url.startsWith("http://") || url.startsWith("https://");
        for (String line : sec.getStringList("message")) {
            Component c = Text.c(line.replace("{url}", url));
            if (clickable) {
                c = c.clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Text.c("&7Clique para abrir")));
            }
            s.sendMessage(c);
        }
        return true;
    }

    /** /duelo <jogador> (/duelar): abre o menu de modalidades para desafiar. */
    private boolean duelo(CommandSender s, String[] a) {
        Messages msg = plugin.messages();
        if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
        if (a.length == 0) { msg.send(p, "duel-usage"); return true; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) { msg.send(p, "player-not-found"); return true; }
        plugin.challenges().openMenu(p, t);
        return true;
    }

    /** /dueloaceitar [jogador] */
    private boolean dueloAceitar(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { plugin.messages().send(s, "player-only"); return true; }
        plugin.challenges().accept(p, a.length > 0 ? a[0] : null);
        return true;
    }

    /** /setspawn ou /gcpc setspawn */
    private boolean setSpawn(CommandSender s) {
        Messages msg = plugin.messages();
        if (!s.hasPermission(ADMIN)) { msg.send(s, "no-permission"); return true; }
        if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
        plugin.data().setSpawn(p.getLocation());
        msg.send(s, "spawn-set");
        return true;
    }

    /** /spawn: volta ao spawn do practice. */
    private boolean spawn(CommandSender s) {
        Messages msg = plugin.messages();
        if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
        if (plugin.duels().isInDuel(p)) { msg.send(p, "spawn-in-duel"); return true; }
        plugin.lobby().teleportSpawn(p);
        msg.send(p, "spawn-teleported");
        return true;
    }

    /** /lobby: sai da fila e executa o comando configurado (padrao: /queue lobby) ou conecta direto ao servidor. */
    private boolean lobby(CommandSender s) {
        Messages msg = plugin.messages();
        if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
        var cfg = plugin.getConfig();
        if (plugin.duels().isInDuel(p) && cfg.getBoolean("lobby.block-in-duel", true)) {
            msg.send(p, "lobby-in-duel");
            return true;
        }
        plugin.queue().leave(p, true);
        msg.send(p, "lobby-sending");
        if (cfg.getString("lobby.type", "COMMAND").equalsIgnoreCase("CONNECT")) {
            plugin.connect(p, cfg.getString("lobby.server", "lobby"));
        } else {
            String c = cfg.getString("lobby.command", "queue lobby");
            if (c.startsWith("/")) c = c.substring(1);
            p.performCommand(c);
        }
        return true;
    }

    /** /party <...> ou /gcpc party <...> */
    private boolean party(CommandSender s, String[] a) {
        Messages msg = plugin.messages();
        if (!(s instanceof Player p)) { msg.send(s, "player-only"); return true; }
        PartyManager pm = plugin.parties();
        if (a.length == 0) { plugin.partyMenu().open(p); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create", "criar" -> pm.create(p);
            case "invite", "convidar", "add" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) msg.send(p, "party-player-not-found"); else pm.invite(p, t);
            }
            case "accept", "aceitar" -> {
                Player l = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (l == null) msg.send(p, "party-invite-none"); else pm.accept(p, l.getUniqueId());
            }
            case "join", "entrar" -> {
                Player l = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (l == null) msg.send(p, "party-player-not-found"); else pm.join(p, l.getUniqueId());
            }
            case "kick", "remover" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) msg.send(p, "party-player-not-found"); else pm.kick(p, t.getUniqueId());
            }
            case "leave", "sair" -> pm.leave(p, false);
            case "disband", "desfazer" -> pm.disband(p);
            case "public", "publica" -> pm.setOpen(p, true);
            case "private", "privada" -> pm.setOpen(p, false);
            case "info", "list", "lista" -> pm.info(p);
            default -> msg.send(p, "party-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String cn = cmd.getName().toLowerCase(Locale.ROOT);
        if (cn.equals("duelo") || cn.equals("dueloaceitar")) {
            if (a.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(p.getName());
                }
            }
            return out;
        }
        if (cn.equals("spawn") || cn.equals("setspawn") || cn.equals("lobby")
                || cn.equals("discord") || cn.equals("site") || cn.equals("loja")) return out;
        boolean partyCmd = cmd.getName().equalsIgnoreCase("party");
        String[] args = a;
        if (!partyCmd && a.length >= 1 && a[0].equalsIgnoreCase("party") && a.length >= 2) {
            partyCmd = true;
            args = Arrays.copyOfRange(a, 1, a.length);
        }
        if (partyCmd) {
            if (args.length == 1) {
                for (String x : PARTY_SUBS) if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(x);
            } else if (args.length == 2 && List.of("invite", "accept", "join", "kick").contains(args[0].toLowerCase(Locale.ROOT))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
                }
            }
            return out;
        }
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("party", "stats", "leave", "spawn", "lobby"));
            if (s.hasPermission(ADMIN)) subs.addAll(List.of("reload", "setspawn", "setkit"));
            for (String x : subs) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && s.hasPermission(ADMIN)
                && List.of("setkit", "editor", "kit").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (String id : plugin.modes().ids()) if (id.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(id);
        }
        return out;
    }
}
