package ru.privatenull.pnrelog.display;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.display.CombatBoardProvider;
import ru.privatenull.pnrelog.api.display.CombatDisplayApi;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.text.Colorizer;
import ru.privatenull.pnrelog.text.PlaceholderSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CombatDisplay implements CombatDisplayApi {
    private final Map<UUID, BossBar> bossBars = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.logging.Logger logger;
    private volatile CombatBoardProvider scoreboardProvider;
    private PluginSettings settings;

    public CombatDisplay(PluginSettings settings, java.util.logging.Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.scoreboardProvider = createProvider(settings.display().scoreboard().provider());
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
        clear();
        this.scoreboardProvider = createProvider(settings.display().scoreboard().provider());
    }

    public void show(Player player, CombatSnapshot snapshot, long now) {
        PluginSettings.Display display = settings.display();
        Map<String, String> placeholders = placeholders(snapshot, now);
        if (display.actionbar().enabled()) {
            String text = PlaceholderSupport.parse(player, replace(display.actionbar().text(), placeholders));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(Colorizer.color(text)));
        }
        if (!display.bossbar().enabled()) {
            BossBar old = bossBars.remove(player.getUniqueId());
            if (old != null) old.removeAll();
        } else {
            BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), ignored -> {
                BossBar created = Bukkit.createBossBar("", display.bossbar().color(), display.bossbar().style());
                created.addPlayer(player);
                return created;
            });
            if (!bar.getPlayers().contains(player)) {
                bar.removeAll();
                bar.addPlayer(player);
            }
            bar.setTitle(Colorizer.color(PlaceholderSupport.parse(player,
                    replace(display.bossbar().title(), placeholders))));
            bar.setColor(display.bossbar().color());
            bar.setStyle(display.bossbar().style());
            double progress = display.bossbar().progress()
                    ? snapshot.remainingMillis(now) / (double) settings.combat().durationMillis() : 1D;
            bar.setProgress(Math.max(0D, Math.min(1D, progress)));
            bar.setVisible(true);
        }
        showScoreboard(player, snapshot, now);
    }

    public void hide(UUID playerId) {
        BossBar bar = bossBars.remove(playerId);
        if (bar != null) bar.removeAll();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) scoreboardProvider.hide(player);
    }

    public void clear() {
        for (BossBar bar : bossBars.values()) bar.removeAll();
        bossBars.clear();
        scoreboardProvider.close();
    }

    @Override
    public void setScoreboardProvider(CombatBoardProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider cannot be null");
        scoreboardProvider.close();
        scoreboardProvider = provider;
    }

    @Override
    public CombatBoardProvider getScoreboardProvider() {
        return scoreboardProvider;
    }

    private void showScoreboard(Player player, CombatSnapshot snapshot, long now) {
        PluginSettings.Scoreboard board = settings.display().scoreboard();
        if (!board.enabled()) {
            scoreboardProvider.hide(player);
            return;
        }
        Map<String, String> values = placeholders(snapshot, now);
        List<String> lines = new java.util.ArrayList<>();
        for (String raw : board.lines()) {
            if (raw.contains("{opponents}")) {
                if (snapshot.opponents().isEmpty()) lines.add(Colorizer.color(board.empty()));
                else for (OpponentSnapshot opponent : snapshot.opponents()) {
                    Player opponentPlayer = Bukkit.getPlayer(opponent.playerId());
                    String health = opponentPlayer == null ? "0" : String.format(Locale.US, "%.1f", opponentPlayer.getHealth());
                    String ping = opponentPlayer == null ? "0" : Integer.toString(opponentPlayer.getPing());
                    Player papiContext = opponentPlayer == null ? player : opponentPlayer;
                    lines.add(Colorizer.color(PlaceholderSupport.parse(papiContext, board.opponent()
                            .replace("{name}", opponent.playerName())
                            .replace("{health}", health)
                            .replace("{ping}", ping))));
                }
            } else lines.add(Colorizer.color(PlaceholderSupport.parse(player, replace(raw, values))));
        }
        try {
            scoreboardProvider.show(player, Colorizer.color(PlaceholderSupport.parse(player,
                    replace(board.title(), values))), lines);
        } catch (RuntimeException exception) {
            logger.warning("Scoreboard provider отключён: " + exception.getMessage());
            scoreboardProvider = new BukkitBoardProvider();
            scoreboardProvider.show(player, Colorizer.color(PlaceholderSupport.parse(player,
                    replace(board.title(), values))), lines);
        }
    }

    private CombatBoardProvider createProvider(PluginSettings.ScoreboardProvider requested) {
        if ((requested == PluginSettings.ScoreboardProvider.STERNAL_BOARD
                || requested == PluginSettings.ScoreboardProvider.AUTO)
                && Bukkit.getPluginManager().isPluginEnabled("SternalBoard")) {
            try {
                return new SternalBoardProvider();
            } catch (ReflectiveOperationException exception) {
                logger.warning("SternalBoard недоступен: " + exception.getMessage());
            }
        }
        if (requested != PluginSettings.ScoreboardProvider.BUKKIT
                && requested != PluginSettings.ScoreboardProvider.STERNAL_BOARD
                && Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            try {
                return new TabBoardProvider();
            } catch (ReflectiveOperationException exception) {
                logger.warning("TAB scoreboard недоступен, используется Bukkit: " + exception.getMessage());
            }
        }
        return new BukkitBoardProvider();
    }

    private static Map<String, String> placeholders(CombatSnapshot snapshot, long now) {
        String opponents = snapshot.opponents().stream()
                .map(OpponentSnapshot::playerName)
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        String primary = snapshot.opponents().isEmpty() ? "-" : snapshot.opponents().get(0).playerName();
        return Map.of(
                "time", Long.toString(secondsCeil(snapshot.remainingMillis(now))),
                "opponents", opponents,
                "opponent", primary,
                "damage_dealt", String.format(Locale.US, "%.1f", snapshot.damageDealt()),
                "damage_taken", String.format(Locale.US, "%.1f", snapshot.damageTaken())
        );
    }

    private static String replace(String input, Map<String, String> placeholders) {
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private static long secondsCeil(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }
}
