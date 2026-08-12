package ru.privatenull.pnrelog.combat;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.TagCause;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CombatGraphTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void keepsPairDeadlinesIndependent() {
        CombatGraph graph = new CombatGraph();
        graph.tag(A, "A", B, "B", 0L, 1_000L, 0L, 2D, TagCause.MELEE);
        graph.tag(B, "B", C, "C", 500L, 1_000L, 0L, 3D, TagCause.MELEE);

        Map<UUID, CombatSnapshot> ended = graph.prune(1_001L);

        assertTrue(ended.containsKey(A));
        assertFalse(ended.containsKey(B));
        assertFalse(graph.isInCombat(A, 1_001L));
        assertTrue(graph.isInCombat(B, 1_001L));
        assertEquals(1_500L, graph.snapshot(B, 1_001L).orElseThrow().expiresAt());
    }

    @Test
    void capsRefreshAtMaximumLinkLifetime() {
        CombatGraph graph = new CombatGraph();
        graph.tag(A, "A", B, "B", 1_000L, 2_000L, 2_000L, 1D, TagCause.MELEE);
        graph.tag(A, "A", B, "B", 2_500L, 1_000L, 2_000L, 1D, TagCause.MELEE);

        assertEquals(3_000L, graph.snapshot(A, 2_500L).orElseThrow().expiresAt());
        assertFalse(graph.isInCombat(A, 3_000L));
    }

    @Test
    void tracksDirectionalDamageAndLastAggressor() {
        CombatGraph graph = new CombatGraph();
        graph.tag(A, "A", B, "B", 1_000L, 5_000L, 0L, 4.5D, TagCause.MELEE);
        graph.tag(B, "B", A, "A", 1_500L, 5_000L, 0L, 2D, TagCause.PROJECTILE);

        CombatSnapshot a = graph.snapshot(A, 1_500L).orElseThrow();
        assertEquals(4.5D, a.damageDealt(), 0.0001D);
        assertEquals(2D, a.damageTaken(), 0.0001D);
        assertEquals(B, a.lastAggressor());
        assertEquals(1, a.hitsDealt());
        assertEquals(1, a.hitsTaken());
    }

    @Test
    void removingMiddlePlayerKeepsOtherCombatAlive() {
        CombatGraph graph = new CombatGraph();
        graph.tag(A, "A", B, "B", 0L, 10_000L, 0L, 1D, TagCause.MELEE);
        graph.tag(B, "B", C, "C", 0L, 10_000L, 0L, 1D, TagCause.MELEE);
        graph.tag(A, "A", C, "C", 0L, 10_000L, 0L, 1D, TagCause.MELEE);

        Map<UUID, CombatSnapshot> ended = graph.removePlayer(B, 100L);

        assertTrue(ended.containsKey(B));
        assertFalse(ended.containsKey(A));
        assertFalse(ended.containsKey(C));
        assertTrue(graph.hasLink(A, C, 100L));
    }

    @Test
    void expiresEveryStandaloneCombatInOnePass() {
        CombatGraph graph = new CombatGraph();
        assertTrue(graph.tagSingle(A, "A", 0L, 1_000L, 0L));
        assertTrue(graph.tagSingle(B, "B", 0L, 1_000L, 0L));

        Map<UUID, CombatSnapshot> ended = graph.prune(1_000L);

        assertEquals(2, ended.size());
        assertTrue(ended.get(A).opponents().isEmpty());
        assertFalse(graph.isInCombat(A, 1_000L));
        assertFalse(graph.isInCombat(B, 1_000L));
    }
}
