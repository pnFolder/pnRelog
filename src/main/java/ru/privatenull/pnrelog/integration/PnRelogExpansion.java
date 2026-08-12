package ru.privatenull.pnrelog.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.combat.CombatService;

import java.util.Locale;
import java.util.Optional;

public final class PnRelogExpansion extends PlaceholderExpansion {
    private final CombatService combat;
    private final String version;

    public PnRelogExpansion(CombatService combat, String version) {
        this.combat = combat;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pnrelog";
    }

    @Override
    public @NotNull String getAuthor() {
        return "inventoryType";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (player == null) return "";
        String key = parameters.toLowerCase(Locale.ROOT);
        OfflinePlayer requested = player;
        if (key.startsWith("player_")) {
            String remainder = parameters.substring("player_".length());
            int separator = remainder.indexOf('_');
        if (separator < 1) return combat.settings().placeholders().error();
            requested = org.bukkit.Bukkit.getOfflinePlayer(remainder.substring(0, separator));
            key = remainder.substring(separator + 1).toLowerCase(Locale.ROOT);
        }
        Optional<CombatSnapshot> optional = combat.getCachedCombat(requested.getUniqueId())
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
