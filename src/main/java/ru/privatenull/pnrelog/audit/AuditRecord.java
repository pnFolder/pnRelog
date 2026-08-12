package ru.privatenull.pnrelog.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditRecord(Instant at, String type, UUID playerId, String playerName, String detail) {
}
