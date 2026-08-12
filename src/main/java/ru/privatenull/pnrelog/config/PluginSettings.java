package ru.privatenull.pnrelog.config;

import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.privatenull.pnrelog.api.powerup.PowerupType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.net.URI;
import java.net.URISyntaxException;

public record PluginSettings(
        boolean metrics,
        String locale,
        String textFormat,
        boolean usePlaceholderApi,
        Combat combat,
        Powerups powerups,
        Regions regions,
        Updates updates,
        Placeholders placeholders,
        Audit audit,
        Display display,
        Restrictions restrictions,
        Logout logout,
        Safety safety
) {
    public enum CommandMode { BLACKLIST, WHITELIST }

    public enum OpponentSelection { HIGHEST_DAMAGE, LAST_AGGRESSOR }

    public record Combat(
            long durationMillis,
            long maxLinkLifetimeMillis,
            double minimumEffectiveDamage,
            Set<String> ignoredWorlds,
            Set<GameMode> ignoredGameModes,
            boolean detectProjectiles,
            boolean detectExplosions,
            boolean detectPets,
            boolean detectAreaEffects,
            Set<org.bukkit.entity.EntityType> ignoredProjectiles,
            long tickIntervalTicks
    ) {
    }

    public record Powerups(String provider, Set<PowerupType> preventAttacker, Set<PowerupType> preventTarget,
                           Set<PowerupType> disableAttacker, Set<PowerupType> disableTarget) {
    }

    public record Regions(boolean enabled, String provider, Set<String> worlds) {
    }

    public record Updates(boolean enabled, long checkIntervalTicks,
                          boolean downloadEnabled) {
    }

    public record Placeholders(String error, String trueText, String falseText, String delimiter) {
    }

    public record Audit(boolean enabled, String file, long maxFileBytes, int recentRecords,
                        boolean webhookEnabled, URI webhookUrl) {
    }

    public record Actionbar(boolean enabled, String text) {
    }

    public record Bossbar(boolean enabled, String title, BarColor color, BarStyle style, boolean progress) {
    }

    public enum ScoreboardProvider { AUTO, BUKKIT, TAB, STERNAL_BOARD }

    public record Scoreboard(boolean enabled, ScoreboardProvider provider, String title,
                             List<String> lines, String opponent, String empty) {
    }

    public record Display(Actionbar actionbar, Bossbar bossbar, Scoreboard scoreboard) {
    }

    public record Commands(boolean enabled, CommandMode mode, List<String> entries,
                           boolean filterTabComplete, List<String> targetingPrefixes) {
    }

    public record Teleports(boolean enabled, Set<PlayerTeleportEvent.TeleportCause> blockedCauses) {
    }

    public record Restrictions(Commands commands, Teleports teleports, boolean blockElytra) {
    }

    public record Penalty(
            boolean kill,
            boolean broadcast,
            List<String> consoleCommands,
            List<String> opponentCommands,
            OpponentSelection opponentSelection
    ) {
    }

    public record Logout(
            boolean punishQuits,
            boolean punishKicks,
            List<String> ignoredKickReasons,
            long reconnectGraceMillis,
            long reconnectCombatMillis,
            Penalty penalty
    ) {
    }

    public record CircuitBreaker(
            boolean enabled,
            long windowMillis,
            int minimumDisconnects,
            double onlineFraction,
            long openMillis
    ) {
    }

    public record Safety(CircuitBreaker circuitBreaker) {
    }


    public static PluginSettings load(FileConfiguration config) {
        long duration = seconds(config, "combat.duration-seconds", 20, 1, 3600);
        long maxLifetime = seconds(config, "combat.max-link-lifetime-seconds", 180, 0, 86400);
        if (maxLifetime > 0L && maxLifetime < duration) {
            throw new IllegalArgumentException("combat.max-link-lifetime-seconds cannot be shorter than combat.duration-seconds");
        }
        double minimumDamage = config.getDouble("combat.minimum-effective-damage", 0.01D);
        if (!Double.isFinite(minimumDamage) || minimumDamage < 0D) {
            throw new IllegalArgumentException("combat.minimum-effective-damage must be a finite non-negative number");
        }

        Combat combat = new Combat(
                duration,
                maxLifetime,
                minimumDamage,
                lowerCaseSet(config.getStringList("combat.ignored-worlds")),
                enumSet(config.getStringList("combat.ignored-gamemodes"), GameMode.class,
                        "combat.ignored-gamemodes"),
                config.getBoolean("combat.detection.projectiles", true),
                config.getBoolean("combat.detection.explosions", true),
                config.getBoolean("combat.detection.pets", true),
                config.getBoolean("combat.detection.area-effects", true),
                enumSet(config.getStringList("combat.detection.ignored-projectiles"),
                        org.bukkit.entity.EntityType.class, "combat.detection.ignored-projectiles"),
                integer(config, "combat.tick-interval-ticks", 5, 1, 1200)
        );
        Powerups powerups = new Powerups(
                config.getString("powerups.provider", "AUTO").strip().toUpperCase(Locale.ROOT),
                enumSet(config.getStringList("powerups.prevent-start.attacker"), PowerupType.class,
                        "powerups.prevent-start.attacker"),
                enumSet(config.getStringList("powerups.prevent-start.target"), PowerupType.class,
                        "powerups.prevent-start.target"),
                enumSet(config.getStringList("powerups.disable-on-start.attacker"), PowerupType.class,
                        "powerups.disable-on-start.attacker"),
                enumSet(config.getStringList("powerups.disable-on-start.target"), PowerupType.class,
                        "powerups.disable-on-start.target")
        );
        Regions regions = new Regions(
                config.getBoolean("regions.enabled", false),
                config.getString("regions.provider", "AUTO").strip().toUpperCase(Locale.ROOT),
                lowerCaseSet(config.getStringList("regions.worlds"))
        );
        int updateHours = integer(config, "updates.check-interval-hours", 6, 1, 168);
        Updates updates = new Updates(
                config.getBoolean("updates.enabled", true),
                updateHours * 60L * 60L * 20L,
                config.getBoolean("updates.download-enabled", true)
        );
        Placeholders placeholders = new Placeholders(
                config.getString("placeholders.error", "N/A"),
                config.getString("placeholders.true", "&aДа"),
                config.getString("placeholders.false", "&cНет"),
                config.getString("placeholders.delimiter", ", ")
        );
        String auditFile = requiredString(config, "audit.file");
        if (auditFile.contains("..") || auditFile.contains("/") || auditFile.contains("\\")) {
            throw new IllegalArgumentException("audit.file must be a file name inside the plugin directory");
        }
        boolean webhookEnabled = config.getBoolean("audit.webhook.enabled", false);
        URI webhookUrl = null;
        if (webhookEnabled) {
            try {
                webhookUrl = new URI(config.getString("audit.webhook.url", ""));
                if (!("http".equalsIgnoreCase(webhookUrl.getScheme())
                        || "https".equalsIgnoreCase(webhookUrl.getScheme()))
                        || webhookUrl.getHost() == null) {
                    throw new IllegalArgumentException("audit.webhook.url must be an HTTP(S) URL");
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("audit.webhook.url is invalid", exception);
            }
        }
        Audit audit = new Audit(config.getBoolean("audit.enabled", true), auditFile,
                integer(config, "audit.max-file-size-megabytes", 16, 1, 1024) * 1024L * 1024L,
                integer(config, "audit.recent-records", 100, 10, 10000), webhookEnabled, webhookUrl);

        Display display = new Display(
                new Actionbar(
                        config.getBoolean("display.actionbar.enabled", true),
                        requiredString(config, "display.actionbar.text")
                ),
                new Bossbar(
                        config.getBoolean("display.bossbar.enabled", true),
                        requiredString(config, "display.bossbar.title"),
                        enumValue(config.getString("display.bossbar.color", "RED"), BarColor.class,
                                "display.bossbar.color"),
                        enumValue(config.getString("display.bossbar.style", "SOLID"), BarStyle.class,
                                "display.bossbar.style"),
                        config.getBoolean("display.bossbar.progress", true)
                ),
                new Scoreboard(
                        config.getBoolean("display.scoreboard.enabled", true),
                        enumValue(config.getString("display.scoreboard.provider", "AUTO"),
                                ScoreboardProvider.class, "display.scoreboard.provider"),
                        requiredString(config, "display.scoreboard.title"),
                        List.copyOf(config.getStringList("display.scoreboard.lines")),
                        requiredString(config, "display.scoreboard.opponent"),
                        requiredString(config, "display.scoreboard.empty")
                )
        );

        List<String> entries = normalizedCommands(config.getStringList("restrictions.commands.entries"));
        Commands commands = new Commands(
                config.getBoolean("restrictions.commands.enabled", true),
                enumValue(config.getString("restrictions.commands.mode", "BLACKLIST"), CommandMode.class,
                        "restrictions.commands.mode"),
                entries,
                config.getBoolean("restrictions.commands.filter-tab-complete", true),
                normalizedCommands(config.getStringList("restrictions.commands.targeting-prefixes"))
        );
        Teleports teleports = new Teleports(
                config.getBoolean("restrictions.teleports.enabled", true),
                enumSet(config.getStringList("restrictions.teleports.blocked-causes"),
                        PlayerTeleportEvent.TeleportCause.class, "restrictions.teleports.blocked-causes")
        );

        Penalty penalty = new Penalty(
                config.getBoolean("logout.penalty.kill", true),
                config.getBoolean("logout.penalty.broadcast", true),
                commandTemplates(config.getStringList("logout.penalty.console-commands"),
                        "logout.penalty.console-commands"),
                commandTemplates(config.getStringList("logout.penalty.opponent-commands"),
                        "logout.penalty.opponent-commands"),
                enumValue(config.getString("logout.penalty.opponent-selection", "HIGHEST_DAMAGE"),
                        OpponentSelection.class, "logout.penalty.opponent-selection")
        );
        List<String> ignoredReasons = new ArrayList<>();
        for (String reason : config.getStringList("logout.ignored-kick-reasons")) {
            String normalized = reason.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) ignoredReasons.add(normalized);
        }
        Logout logout = new Logout(
                config.getBoolean("logout.punish-quits", true),
                config.getBoolean("logout.punish-kicks", false),
                List.copyOf(ignoredReasons),
                seconds(config, "logout.reconnect-grace-seconds", 5, 0, 300),
                seconds(config, "logout.reconnect-combat-seconds", 15, 1, 3600),
                penalty
        );

        CircuitBreaker breaker = new CircuitBreaker(
                config.getBoolean("safety.circuit-breaker.enabled", true),
                seconds(config, "safety.circuit-breaker.window-seconds", 4, 1, 60),
                integer(config, "safety.circuit-breaker.minimum-disconnects", 3, 2, 10000),
                finiteRange(config, "safety.circuit-breaker.online-fraction", 0.5D, 0.01D, 1D),
                seconds(config, "safety.circuit-breaker.open-seconds", 30, 1, 3600)
        );

        String locale = config.getString("locale", "ru").strip().toLowerCase(Locale.ROOT);
        if (!Set.of("ru", "en").contains(locale)) throw new IllegalArgumentException("locale must be ru or en");
        String textFormat = config.getString("text-format", "LEGACY").strip().toUpperCase(Locale.ROOT);
        if (!Set.of("LEGACY", "MINIMESSAGE").contains(textFormat)) {
            throw new IllegalArgumentException("text-format must be LEGACY or MINIMESSAGE");
        }
        return new PluginSettings(config.getBoolean("metrics", true), locale, textFormat,
                config.getBoolean("use-placeholderapi", false), combat, powerups, regions, updates,
                placeholders, audit, display, new Restrictions(commands, teleports,
                config.getBoolean("restrictions.block-elytra", true)), logout, new Safety(breaker));
    }

    private static long seconds(FileConfiguration config, String path, int fallback, int min, int max) {
        return integer(config, path, fallback, min, max) * 1000L;
    }

    private static int integer(FileConfiguration config, String path, int fallback, int min, int max) {
        int value = config.getInt(path, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static double finiteRange(FileConfiguration config, String path, double fallback,
                                      double min, double max) {
        double value = config.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static String requiredString(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " cannot be empty");
        return value;
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static List<String> normalizedCommands(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static List<String> commandTemplates(List<String> values, String path) {
        Set<String> allowed = Set.of("player", "uuid", "opponent", "opponent_uuid",
                "damage_dealt", "damage_taken");
        List<String> result = new ArrayList<>();
        for (String raw : values) {
            String value = raw.strip();
            if (value.startsWith("/")) value = value.substring(1);
            if (value.isBlank()) continue;
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(path + " contains a line break");
            }
            int index = 0;
            while ((index = value.indexOf('{', index)) >= 0) {
                int end = value.indexOf('}', index + 1);
                if (end < 0) throw new IllegalArgumentException(path + " contains an unclosed placeholder");
                String placeholder = value.substring(index + 1, end);
                if (!allowed.contains(placeholder)) {
                    throw new IllegalArgumentException(path + " contains unknown placeholder {" + placeholder + "}");
                }
                index = end + 1;
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, String path) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + " contains unknown value: " + value);
        }
    }

    private static <E extends Enum<E>> Set<E> enumSet(List<String> values, Class<E> type, String path) {
        Set<E> result = EnumSet.noneOf(type);
        for (String value : values) result.add(enumValue(value, type, path));
        return Set.copyOf(result);
    }
}
