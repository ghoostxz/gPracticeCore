package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class Messages {
    private final GCelestialPracticeCore plugin;

    public Messages(GCelestialPracticeCore plugin) {
        this.plugin = plugin;
    }

    private String text(String key, String... kv) {
        String s = plugin.getConfig().getString("messages." + key, "&cMensagem ausente: " + key);
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    /** Mensagem com prefixo. */
    public Component get(String key, String... kv) {
        return Text.c(plugin.getConfig().getString("prefix", "") + text(key, kv));
    }

    /** Mensagem sem prefixo (titles, etc). */
    public Component raw(String key, String... kv) {
        return Text.c(text(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        if (to != null) to.sendMessage(get(key, kv));
    }
}
