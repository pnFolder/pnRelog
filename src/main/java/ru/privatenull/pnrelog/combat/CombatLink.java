package ru.privatenull.pnrelog.combat;

import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.TagCause;

import java.util.UUID;

final class CombatLink {
    private final CombatPair pair;
    private final long createdAt;
    private String firstName;
    private String secondName;
    private long expiresAt;
    private long lastHitAt;
    private UUID lastAggressor;
    private TagCause lastCause;
    private double firstToSecondDamage;
    private double secondToFirstDamage;
    private int firstToSecondHits;
    private int secondToFirstHits;
    private long firstToSecondLastHitAt;
    private long secondToFirstLastHitAt;

    CombatLink(CombatPair pair, String firstName, String secondName, long now) {
        this.pair = pair;
        this.firstName = firstName;
        this.secondName = secondName;
        this.createdAt = now;
        this.expiresAt = now;
        this.lastHitAt = now;
    }

    void refresh(UUID attacker, String attackerName, String targetName, long now, long durationMillis,
                 long maxLifetimeMillis, double effectiveDamage, TagCause cause) {
        boolean attackerIsFirst = pair.first().equals(attacker);
        if (attackerIsFirst) {
            firstName = attackerName;
            secondName = targetName;
        } else {
            secondName = attackerName;
            firstName = targetName;
        }
        long proposedExpiry = saturatingAdd(now, durationMillis);
        if (maxLifetimeMillis > 0L) {
            proposedExpiry = Math.min(proposedExpiry, saturatingAdd(createdAt, maxLifetimeMillis));
        }
        expiresAt = Math.max(expiresAt, proposedExpiry);
        lastAggressor = attacker;
        lastHitAt = now;
        lastCause = cause;
        if (effectiveDamage > 0D) {
            if (attackerIsFirst) {
                firstToSecondDamage += effectiveDamage;
                firstToSecondHits++;
                firstToSecondLastHitAt = now;
            } else {
                secondToFirstDamage += effectiveDamage;
                secondToFirstHits++;
                secondToFirstLastHitAt = now;
            }
        }
    }

    boolean active(long now) {
        return expiresAt > now;
    }

    CombatPair pair() {
        return pair;
    }

    long expiresAt() {
        return expiresAt;
    }

    UUID lastAggressor() {
        return lastAggressor;
    }

    long lastHitAgainst(UUID playerId) {
        if (pair.first().equals(playerId)) return secondToFirstLastHitAt;
        if (pair.second().equals(playerId)) return firstToSecondLastHitAt;
        throw new IllegalArgumentException("Player is not part of this combat link");
    }

    TagCause lastCause() {
        return lastCause;
    }

    OpponentSnapshot snapshotFor(UUID playerId) {
        if (pair.first().equals(playerId)) {
            return new OpponentSnapshot(pair.second(), secondName, expiresAt,
                    firstToSecondDamage, secondToFirstDamage,
                    firstToSecondHits, secondToFirstHits, lastHitAt, secondToFirstLastHitAt);
        }
        if (pair.second().equals(playerId)) {
            return new OpponentSnapshot(pair.first(), firstName, expiresAt,
                    secondToFirstDamage, firstToSecondDamage,
                    secondToFirstHits, firstToSecondHits, lastHitAt, firstToSecondLastHitAt);
        }
        throw new IllegalArgumentException("Player is not part of this combat link");
    }

    String nameOf(UUID playerId) {
        if (pair.first().equals(playerId)) return firstName;
        if (pair.second().equals(playerId)) return secondName;
        throw new IllegalArgumentException("Player is not part of this combat link");
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
