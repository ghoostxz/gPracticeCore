package br.gcelestial.practicecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    public static Component c(String s) {
        return L.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> c(List<String> list) {
        List<Component> out = new ArrayList<>();
        for (String s : list) out.add(c(s));
        return out;
    }
}
