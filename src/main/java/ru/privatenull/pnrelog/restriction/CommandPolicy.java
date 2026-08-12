package ru.privatenull.pnrelog.restriction;

import ru.privatenull.pnrelog.config.PluginSettings;

import java.util.Locale;

public final class CommandPolicy {
    private CommandPolicy() {
    }

    public static boolean blocked(String rawCommand, PluginSettings.Commands settings) {
        String command = normalize(rawCommand);
        if (command.isEmpty()) return false;
        String withoutNamespace = settings.mode() == PluginSettings.CommandMode.BLACKLIST
                ? stripNamespace(command) : command;
        boolean match = settings.entries().stream().anyMatch(entry -> matches(command, entry)
                || matches(withoutNamespace, entry));
        return settings.mode() == PluginSettings.CommandMode.BLACKLIST ? match : !match;
    }

    public static String root(String entry) {
        String normalized = normalize(entry);
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }

    private static boolean matches(String command, String entry) {
        return command.equals(entry) || command.startsWith(entry + " ");
    }

    private static String normalize(String input) {
        String normalized = input == null ? "" : input.strip().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static String stripNamespace(String command) {
        int space = command.indexOf(' ');
        String root = space < 0 ? command : command.substring(0, space);
        int colon = root.indexOf(':');
        if (colon < 0) return command;
        String stripped = root.substring(colon + 1);
        return space < 0 ? stripped : stripped + command.substring(space);
    }
}
