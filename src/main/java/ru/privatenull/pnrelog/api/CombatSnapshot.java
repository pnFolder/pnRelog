package ru.privatenull.pnrelog.api;

import java.util.List;
import java.util.UUID;

public record CombatSnapshot(
        UUID playerId,
        String playerName,
        long capturedAt,
        long expiresAt,
        List<OpponentSnapshot> opponents,
        double damageDealt,
        double damageTaken,
        int hitsDealt,
        int hitsTaken,
        UUID lastAggressor
) {
    public CombatSnapshot {
        opponents = List.copyOf(opponents);
    }

    public long remainingMillis(long now) {
        return Math.max(0L, expiresAt - now);
    }

    public boolean active(long now) {
        return expiresAt > now;
    }
}
