package ru.privatenull.pnrelog.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Colorizer {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static volatile boolean miniMessage;

    private Colorizer() {
    }

    public static void configure(String format) {
        miniMessage = "MINIMESSAGE".equalsIgnoreCase(format);
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) return "";
        if (miniMessage) {
            return LegacyComponentSerializer.legacySection().serialize(
                    MiniMessage.miniMessage().deserialize(input));
        }
        Matcher matcher = HEX.matcher(input);
        StringBuffer output = new StringBuffer(input.length() + 16);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) replacement.append('§').append(hex.charAt(i));
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }
}
