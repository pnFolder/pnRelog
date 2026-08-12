package ru.privatenull.pnrelog.combat;

import java.util.Objects;
import java.util.UUID;

record CombatPair(UUID first, UUID second) {
    CombatPair {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) throw new IllegalArgumentException("A combat pair requires two players");
        if (first.compareTo(second) > 0) {
            UUID swap = first;
            first = second;
            second = swap;
        }
    }

    boolean contains(UUID playerId) {
        return first.equals(playerId) || second.equals(playerId);
    }

    UUID other(UUID playerId) {
        if (first.equals(playerId)) return second;
        if (second.equals(playerId)) return first;
        throw new IllegalArgumentException("Player is not part of this combat pair");
    }
}
