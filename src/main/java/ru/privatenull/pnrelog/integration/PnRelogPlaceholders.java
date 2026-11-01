package ru.privatenull.pnrelog.integration;

import org.bukkit.Bukkit;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.combat.CombatService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Значения плейсхолдеров {@code %pnrelog_*%}; публикуются в PlaceholderAPI через pnLibrary. */
public final class PnRelogPlaceholders {
    /** Ключи, которые публикуются в PlaceholderAPI. Хвост после ключа приходит как параметр value. */
    public static final List<String> KEYS = List.of(
            "active", "in", "in_formatted", "time", "time_formatted", "opponents", "opponent_count",
            "damage_dealt", "damage_taken", "hits_dealt", "hits_taken", "last_attacker",
            "opponents_contains", "player");

    private static final String PLAYER_PREFIX = "player_";
    private static final String CONTAINS_MARKER = "_opponents_contains_";
    /** Ключи без параметров, самые длинные первыми: по ним ищется конец «player_<имя>_<ключ>». */
    private static final List<String> SIMPLE_KEYS = KEYS.stream()
            .filter(key -> !key.equals("player") && !key.equals("opponents_contains"))
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private final CombatService combat;

    public PnRelogPlaceholders(CombatService combat) {
        this.combat = combat;
    }

    /** Значение для плейсхолдера {@code key} (+ хвост {@code tail}, может быть null) игрока {@code playerId}. */
    public String resolve(UUID playerId, String key, String tail) {
        if (playerId == null) return "";
        String parameters = tail == null || tail.isEmpty() ? key : key + "_" + tail;
        String lower = parameters.toLowerCase(Locale.ROOT);
        UUID requested = playerId;
        String name = null;
        if (lower.startsWith(PLAYER_PREFIX)) {
            String remainder = parameters.substring(PLAYER_PREFIX.length());
            String remainderLower = remainder.toLowerCase(Locale.ROOT);
            int split = playerKeySplit(remainderLower);
            if (split < 1) return combat.settings().placeholders().error();
            name = remainder.substring(0, split);
            lower = remainderLower.substring(split + 1);
        }
        if (name != null) requested = Bukkit.getOfflinePlayer(name).getUniqueId();
        return value(requested, lower);
    }

    /** Позиция «_» между именем игрока и ключом; имя может содержать «_». */
    private static int playerKeySplit(String remainderLower) {
        int contains = remainderLower.indexOf(CONTAINS_MARKER);
        if (contains >= 1) return contains;
        for (String key : SIMPLE_KEYS) {
            if (remainderLower.endsWith("_" + key) && remainderLower.length() > key.length() + 1) {
                return remainderLower.length() - key.length() - 1;
            }
        }
        return remainderLower.indexOf('_');
    }

    private String value(UUID requested, String key) {
        Optional<CombatSnapshot> optional = combat.getCachedCombat(requested)
                .filter(snapshot -> snapshot.active(combat.now()));
        if (key.equals("active") || key.equals("in")) return optional.isPresent() ? "true" : "false";
        if (key.equals("in_formatted")) return optional.isPresent()
                ? combat.settings().placeholders().trueText() : combat.settings().placeholders().falseText();
        if (optional.isEmpty()) return switch (key) {
            case "time", "opponent_count", "damage_dealt", "damage_taken", "hits_dealt", "hits_taken" -> "0";
            default -> "";
        };
        CombatSnapshot snapshot = optional.get();
        return switch (key) {
            case "time" -> Long.toString(secondsCeil(snapshot.remainingMillis(combat.now())));
            case "time_formatted" -> formatTime(snapshot.remainingMillis(combat.now()));
            case "opponents" -> snapshot.opponents().stream().map(OpponentSnapshot::playerName)
                    .reduce((left, right) -> left + combat.settings().placeholders().delimiter() + right).orElse("");
            case "opponent_count" -> Integer.toString(snapshot.opponents().size());
            case "damage_dealt" -> String.format(Locale.US, "%.1f", snapshot.damageDealt());
            case "damage_taken" -> String.format(Locale.US, "%.1f", snapshot.damageTaken());
            case "hits_dealt" -> Integer.toString(snapshot.hitsDealt());
            case "hits_taken" -> Integer.toString(snapshot.hitsTaken());
            case "last_attacker" -> lastAttacker(snapshot);
            default -> opponentContains(snapshot, key);
        };
    }

    private static String lastAttacker(CombatSnapshot snapshot) {
        if (snapshot.lastAggressor() == null) return "";
        return snapshot.opponents().stream()
                .filter(opponent -> opponent.playerId().equals(snapshot.lastAggressor()))
                .map(OpponentSnapshot::playerName)
                .findFirst().orElse("");
    }

    private static long secondsCeil(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    private String opponentContains(CombatSnapshot snapshot, String key) {
        String prefix = "opponents_contains_";
        if (!key.startsWith(prefix)) return null;
        String requested = key.substring(prefix.length());
        boolean formatted = requested.endsWith("_formatted");
        if (formatted) requested = requested.substring(0, requested.length() - "_formatted".length());
        String name = requested;
        boolean contains = snapshot.opponents().stream()
                .anyMatch(opponent -> opponent.playerName().equalsIgnoreCase(name));
        return formatted ? (contains ? combat.settings().placeholders().trueText()
                : combat.settings().placeholders().falseText()) : Boolean.toString(contains);
    }

    private static String formatTime(long millis) {
        long seconds = secondsCeil(millis);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0L ? minutes + "м " + remainder + "с" : remainder + "с";
    }
}
