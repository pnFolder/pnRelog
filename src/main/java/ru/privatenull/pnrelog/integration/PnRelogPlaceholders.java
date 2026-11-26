package ru.privatenull.pnrelog.integration;

import org.bukkit.Bukkit;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.combat.CombatService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Значения плейсхолдеров {@code %pnrelog_*%}; публикуются в PlaceholderAPI через pnLibrary. */
public class PnRelogPlaceholders {
    /** Ключи, которые публикуются в PlaceholderAPI. Адреса с ником (player, opponents_contains) описаны в {@link PlayerValues} и {@link OpponentsValues}. */
    public static final List<String> KEYS = List.of(
            "active", "in", "in_formatted", "time", "time_formatted", "opponents", "opponent_count",
            "damage_dealt", "damage_taken", "hits_dealt", "hits_taken", "last_attacker");

    private final CombatService combat;

    public PnRelogPlaceholders(CombatService combat) {
        this.combat = combat;
    }

    /** Значение простого ключа ({@link #KEYS}) для игрока; для неизвестного ключа пустая строка. */
    public String value(UUID playerId, String key) {
        if (playerId == null) return "";
        String result = value(playerId, key.toLowerCase(Locale.ROOT), true);
        return result == null ? "" : result;
    }

    /** Подпись «да/нет» для настраиваемого вида {@code _formatted}. */
    public String yesNo(boolean yes) {
        return yes ? combat.settings().placeholders().trueText() : combat.settings().placeholders().falseText();
    }

    /** Есть ли среди противников игрока {@code playerId} игрок с ником {@code name}. */
    public boolean hasOpponent(UUID playerId, String name) {
        return combat.getCachedCombat(playerId).filter(snapshot -> snapshot.active(combat.now()))
                .map(snapshot -> snapshot.opponents().stream()
                        .anyMatch(opponent -> opponent.playerName().equalsIgnoreCase(name)))
                .orElse(false);
    }

    /** Время до конца боя, красиво: {@code 1м 5с}. */
    public String formattedTime(UUID playerId) {
        return combat.getCachedCombat(playerId).filter(snapshot -> snapshot.active(combat.now()))
                .map(snapshot -> formatTime(snapshot.remainingMillis(combat.now()))).orElse("");
    }

    /** Игрок по нику; офлайн-ник даёт UUID, по которому боя нет. */
    public UUID idOf(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    private String value(UUID requested, String key, boolean unused) {
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
            default -> null;
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

    private static String formatTime(long millis) {
        long seconds = secondsCeil(millis);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0L ? minutes + "м " + remainder + "с" : remainder + "с";
    }
}
