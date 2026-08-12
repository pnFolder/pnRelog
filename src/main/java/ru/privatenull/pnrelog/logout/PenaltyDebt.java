package ru.privatenull.pnrelog.logout;

import java.util.UUID;

public record PenaltyDebt(
        UUID playerId,
        String playerName,
        long createdAt,
        UUID opponentId,
        String opponentName
) {
}
