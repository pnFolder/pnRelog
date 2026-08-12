package ru.privatenull.pnrelog.text;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PlaceholderSupport {
    private static volatile boolean enabled;
    private static volatile Method setPlaceholders;

    private PlaceholderSupport() {
    }

    public static void configure(boolean requested) {
        enabled = false;
        setPlaceholders = null;
        if (!requested || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            Class<?> type = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = type.getMethod("setPlaceholders", Player.class, String.class);
            enabled = true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
    }

    public static String parse(Player player, String input) {
        Method method = setPlaceholders;
        if (!enabled || method == null || player == null || input == null || !input.contains("%")) return input;
        try {
            return (String) method.invoke(null, player, input);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return input;
        }
    }
}
