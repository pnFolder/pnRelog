package ru.privatenull.pnrelog.api;

import java.util.UUID;

public record OpponentSnapshot(
        UUID playerId,
        String playerName,
        long expiresAt,
        double damageDealt,
        double damageTaken,
        int hitsDealt,
        int hitsTaken,
        long lastHitAt,
        long lastDamageTakenAt
) {
    public long remainingMillis(long now) {
        return Math.max(0L, expiresAt - now);
    }
}
