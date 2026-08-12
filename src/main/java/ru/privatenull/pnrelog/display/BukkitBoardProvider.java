package ru.privatenull.pnrelog.display;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.privatenull.pnrelog.api.display.CombatBoardProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BukkitBoardProvider implements CombatBoardProvider {
    private static final ChatColor[] UNIQUE = ChatColor.values();
    private final Map<UUID, Scoreboard> combatBoards = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new ConcurrentHashMap<>();

    @Override
    public void show(Player player, String title, List<String> lines) {
        Scoreboard board = combatBoards.computeIfAbsent(player.getUniqueId(), ignored -> {
            previousBoards.put(player.getUniqueId(), player.getScoreboard());
            return Bukkit.getScoreboardManager().getNewScoreboard();
        });
        Objective objective = board.getObjective("pnrelog");
        if (objective == null) {
            objective = board.registerNewObjective("pnrelog", "dummy", truncate(title, 128));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.setDisplayName(truncate(title, 128));
        }
        for (String entry : board.getEntries()) board.resetScores(entry);
        for (Team team : board.getTeams()) team.unregister();
        int limit = Math.min(15, lines.size());
        for (int index = 0; index < limit; index++) {
            String entry = uniqueEntry(index);
            Team team = board.registerNewTeam("line" + index);
            team.addEntry(entry);
            team.setPrefix(truncate(lines.get(index), 128));
            objective.getScore(entry).setScore(limit - index);
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    @Override
    public void hide(Player player) {
        Scoreboard board = combatBoards.remove(player.getUniqueId());
        Scoreboard previous = previousBoards.remove(player.getUniqueId());
        if (board != null && player.getScoreboard() == board) {
            player.setScoreboard(previous == null ? Bukkit.getScoreboardManager().getMainScoreboard() : previous);
        }
    }

    @Override
    public void close() {
        for (UUID playerId : combatBoards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) hide(player);
        }
        combatBoards.clear();
        previousBoards.clear();
    }

    private static String uniqueEntry(int index) {
        ChatColor first = UNIQUE[index % UNIQUE.length];
        ChatColor second = UNIQUE[(index / UNIQUE.length + 1) % UNIQUE.length];
        return first.toString() + second;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, max);
    }
}
