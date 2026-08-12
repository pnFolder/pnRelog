package ru.privatenull.pnrelog.display;

import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.api.display.CombatBoardProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SternalBoardProvider implements CombatBoardProvider {
    private final Constructor<?> constructor;
    private final Method updateTitle;
    private final Method updateLines;
    private final Method delete;
    private final Map<UUID, Object> handlers = new ConcurrentHashMap<>();

    SternalBoardProvider() throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.xism4.sternalboard.SternalBoardHandler");
        constructor = type.getConstructor(Player.class);
        updateTitle = type.getMethod("updateTitle", String.class);
        updateLines = type.getMethod("updateLines", List.class);
        delete = type.getMethod("delete");
    }

    @Override
    public void show(Player player, String title, List<String> lines) {
        try {
            Object handler = handlers.computeIfAbsent(player.getUniqueId(), ignored -> {
                try {
                    return constructor.newInstance(player);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            updateTitle.invoke(handler, title);
            updateLines.invoke(handler, lines);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("SternalBoard update failed", exception);
        }
    }

    @Override
    public void hide(Player player) {
        Object handler = handlers.remove(player.getUniqueId());
        if (handler == null) return;
        try {
            delete.invoke(handler);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("SternalBoard delete failed", exception);
        }
    }
}
