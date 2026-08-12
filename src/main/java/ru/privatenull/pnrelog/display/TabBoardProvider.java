package ru.privatenull.pnrelog.display;

import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.api.display.CombatBoardProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TabBoardProvider implements CombatBoardProvider {
    private final Object tabApi;
    private final Object scoreboardManager;
    private final Method getPlayer;
    private final Method createScoreboard;
    private final Method showScoreboard;
    private final Method resetScoreboard;
    private final ConcurrentHashMap<UUID, Object> boards = new ConcurrentHashMap<>();

    TabBoardProvider() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
        tabApi = apiClass.getMethod("getInstance").invoke(null);
        scoreboardManager = apiClass.getMethod("getScoreboardManager").invoke(tabApi);
        getPlayer = apiClass.getMethod("getPlayer", UUID.class);
        createScoreboard = find(scoreboardManager.getClass(), "createScoreboard", 3);
        showScoreboard = find(scoreboardManager.getClass(), "showScoreboard", 2);
        resetScoreboard = find(scoreboardManager.getClass(), "resetScoreboard", 1);
    }

    @Override
    public void show(Player player, String title, List<String> lines) {
        try {
            Object tabPlayer = getPlayer.invoke(tabApi, player.getUniqueId());
            if (tabPlayer == null) return;
            Object board = createScoreboard.invoke(scoreboardManager,
                    "pnrelog-" + player.getUniqueId(), title, lines);
            showScoreboard.invoke(scoreboardManager, tabPlayer, board);
            boards.put(player.getUniqueId(), board);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("TAB scoreboard call failed", exception);
        }
    }

    @Override
    public void hide(Player player) {
        try {
            Object tabPlayer = getPlayer.invoke(tabApi, player.getUniqueId());
            if (tabPlayer != null) resetScoreboard.invoke(scoreboardManager, tabPlayer);
            boards.remove(player.getUniqueId());
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("TAB scoreboard reset failed", exception);
        }
    }

    private static Method find(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
