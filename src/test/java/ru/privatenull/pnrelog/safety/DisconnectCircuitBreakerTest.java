package ru.privatenull.pnrelog.safety;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnrelog.config.PluginSettings;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisconnectCircuitBreakerTest {
    @Test
    void opensOnlyAfterUniqueThresholdAndClosesByDeadline() {
        DisconnectCircuitBreaker breaker = new DisconnectCircuitBreaker(settings());
        UUID first = UUID.randomUUID();

        assertFalse(breaker.record(first, 0L, 4));
        assertFalse(breaker.record(first, 100L, 4));
        assertTrue(breaker.record(UUID.randomUUID(), 200L, 4));
        assertTrue(breaker.isOpen(1_000L));
        assertFalse(breaker.isOpen(10_200L));
    }

    @Test
    void recalculatesOnlinePeakAfterWindowMoves() {
        DisconnectCircuitBreaker breaker = new DisconnectCircuitBreaker(new PluginSettings.CircuitBreaker(
                true, 1_000L, 2, 0.5D, 10_000L));
        assertFalse(breaker.record(UUID.randomUUID(), 0L, 100));
        assertFalse(breaker.record(UUID.randomUUID(), 1_001L, 4));
        assertTrue(breaker.record(UUID.randomUUID(), 1_002L, 4));
    }

    private static PluginSettings.CircuitBreaker settings() {
        return new PluginSettings.CircuitBreaker(true, 4_000L, 2, 0.5D, 10_000L);
    }
}
