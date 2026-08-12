package ru.privatenull.pnrelog.combat;

import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.TagCause;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Main-thread combat model. It deliberately has no Bukkit dependency. */
public final class CombatGraph {
    private final Map<CombatPair, CombatLink> links = new HashMap<>();
    private final Map<UUID, Set<CombatPair>> byPlayer = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> standalone = new HashMap<>();

    public synchronized boolean hasLink(UUID first, UUID second, long now) {
        CombatLink link = links.get(new CombatPair(first, second));
        return link != null && link.active(now);
    }

    public synchronized boolean isInCombat(UUID playerId, long now) {
        Set<CombatPair> pairs = byPlayer.get(playerId);
        Long standaloneExpiry = standalone.get(playerId);
        if (standaloneExpiry != null && standaloneExpiry > now) return true;
        if (pairs == null) return false;
        for (CombatPair pair : pairs) {
            CombatLink link = links.get(pair);
            if (link != null && link.active(now)) return true;
        }
        return false;
    }

    public synchronized boolean tag(UUID attacker, String attackerName, UUID target, String targetName,
                       long now, long durationMillis, long maxLifetimeMillis,
                       double effectiveDamage, TagCause cause) {
        CombatPair pair = new CombatPair(attacker, target);
        names.put(attacker, attackerName);
        names.put(target, targetName);
        CombatLink existing = links.get(pair);
        boolean created = existing == null || !existing.active(now);
        if (existing != null && !existing.active(now)) removeLink(existing);
        CombatLink link = created
                ? new CombatLink(pair, pair.first().equals(attacker) ? attackerName : targetName,
                pair.second().equals(target) ? targetName : attackerName, now)
                : existing;
        if (created) {
            links.put(pair, link);
            byPlayer.computeIfAbsent(pair.first(), ignored -> new HashSet<>()).add(pair);
            byPlayer.computeIfAbsent(pair.second(), ignored -> new HashSet<>()).add(pair);
        }
        link.refresh(attacker, attackerName, targetName, now, durationMillis,
                maxLifetimeMillis, effectiveDamage, cause);
        return created;
    }

    public synchronized boolean tagSingle(UUID playerId, String playerName, long now,
                                          long durationMillis, long maxLifetimeMillis) {
        names.put(playerId, playerName);
        boolean created = !isInCombat(playerId, now);
        long proposed = saturatingAdd(now, durationMillis);
        Long current = standalone.get(playerId);
        standalone.put(playerId, current == null ? proposed : Math.max(current, proposed));
        return created;
    }

    public synchronized Optional<CombatSnapshot> snapshot(UUID playerId, long now) {
        Set<CombatPair> pairs = byPlayer.get(playerId);
        Long standaloneExpiry = standalone.get(playerId);
        boolean standaloneActive = standaloneExpiry != null && standaloneExpiry > now;
        if ((pairs == null || pairs.isEmpty()) && !standaloneActive) return Optional.empty();
        List<OpponentSnapshot> opponents = new ArrayList<>();
        String playerName = names.getOrDefault(playerId, playerId.toString());
        long expiresAt = 0L;
        double dealt = 0D;
        double taken = 0D;
        int hitsDealt = 0;
        int hitsTaken = 0;
        UUID lastAggressor = null;
        long lastAggressorAt = Long.MIN_VALUE;
        for (CombatPair pair : pairs == null ? Set.<CombatPair>of() : pairs) {
            CombatLink link = links.get(pair);
            if (link == null || !link.active(now)) continue;
            OpponentSnapshot opponent = link.snapshotFor(playerId);
            opponents.add(opponent);
            expiresAt = Math.max(expiresAt, opponent.expiresAt());
            dealt += opponent.damageDealt();
            taken += opponent.damageTaken();
            hitsDealt += opponent.hitsDealt();
            hitsTaken += opponent.hitsTaken();
            long inboundHitAt = opponent.lastDamageTakenAt();
            if (inboundHitAt > lastAggressorAt) {
                lastAggressorAt = inboundHitAt;
                lastAggressor = inboundHitAt == 0L ? null : opponent.playerId();
            }
        }
        if (opponents.isEmpty() && !standaloneActive) return Optional.empty();
        if (standaloneActive) expiresAt = Math.max(expiresAt, standaloneExpiry);
        opponents.sort(Comparator.comparingLong(OpponentSnapshot::lastHitAt).reversed());
        return Optional.of(new CombatSnapshot(playerId, playerName, now, expiresAt, opponents,
                dealt, taken, hitsDealt, hitsTaken, lastAggressor));
    }

    public synchronized Set<UUID> taggedPlayers(long now) {
        Set<UUID> output = new HashSet<>();
        for (UUID playerId : byPlayer.keySet()) {
            if (isInCombat(playerId, now)) output.add(playerId);
        }
        for (Map.Entry<UUID, Long> entry : standalone.entrySet()) {
            if (entry.getValue() > now) output.add(entry.getKey());
        }
        return Set.copyOf(output);
    }

    public synchronized int activeLinkCount(long now) {
        int count = 0;
        for (CombatLink link : links.values()) if (link.active(now)) count++;
        return count;
    }

    public synchronized Map<UUID, CombatSnapshot> prune(long now) {
        Set<UUID> affected = new HashSet<>();
        Map<UUID, List<OpponentSnapshot>> removed = new HashMap<>();
        Map<UUID, Long> removedStandalone = new HashMap<>();
        List<CombatLink> expired = new ArrayList<>();
        for (CombatLink link : links.values()) {
            if (!link.active(now)) expired.add(link);
        }
        for (CombatLink link : expired) {
            for (UUID playerId : List.of(link.pair().first(), link.pair().second())) {
                affected.add(playerId);
                removed.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(link.snapshotFor(playerId));
            }
            removeLink(link);
        }
        List<UUID> expiredStandalone = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : standalone.entrySet()) {
            if (entry.getValue() <= now) expiredStandalone.add(entry.getKey());
        }
        for (UUID playerId : expiredStandalone) {
            long expiry = standalone.remove(playerId);
            affected.add(playerId);
            removed.computeIfAbsent(playerId, ignored -> new ArrayList<>());
            removedStandalone.put(playerId, expiry);
        }
        Map<UUID, CombatSnapshot> output = inactiveSnapshots(affected, removed, now);
        for (Map.Entry<UUID, Long> entry : removedStandalone.entrySet()) {
            UUID playerId = entry.getKey();
            if (isInCombat(playerId, now)) continue;
            String name = names.getOrDefault(playerId, playerId.toString());
            output.putIfAbsent(playerId, new CombatSnapshot(playerId, name, now, entry.getValue(),
                    List.of(), 0D, 0D, 0, 0, null));
            names.remove(playerId);
        }
        return output;
    }

    public synchronized Map<UUID, CombatSnapshot> removePlayer(UUID playerId, long now) {
        Set<CombatPair> playerPairs = byPlayer.get(playerId);
        Long standaloneExpiry = standalone.remove(playerId);
        if ((playerPairs == null || playerPairs.isEmpty()) && standaloneExpiry == null) return Map.of();
        Set<UUID> affected = new HashSet<>();
        Map<UUID, List<OpponentSnapshot>> removed = new HashMap<>();
        List<CombatLink> toRemove = new ArrayList<>();
        for (CombatPair pair : playerPairs == null ? Set.<CombatPair>of() : playerPairs) {
            CombatLink link = links.get(pair);
            if (link == null) continue;
            toRemove.add(link);
            for (UUID participant : List.of(pair.first(), pair.second())) {
                affected.add(participant);
                removed.computeIfAbsent(participant, ignored -> new ArrayList<>())
                        .add(link.snapshotFor(participant));
            }
        }
        for (CombatLink link : toRemove) removeLink(link);
        Map<UUID, CombatSnapshot> output = inactiveSnapshots(affected, removed, now);
        if (standaloneExpiry != null && !isInCombat(playerId, now)) {
            String name = names.getOrDefault(playerId, playerId.toString());
            output.putIfAbsent(playerId, new CombatSnapshot(playerId, name, now, standaloneExpiry,
                    List.of(), 0D, 0D, 0, 0, null));
            names.remove(playerId);
        }
        return output;
    }

    public synchronized Map<UUID, CombatSnapshot> clear(long now) {
        Set<UUID> players = new HashSet<>(byPlayer.keySet());
        players.addAll(standalone.keySet());
        Map<UUID, CombatSnapshot> snapshots = new LinkedHashMap<>();
        for (UUID player : players) snapshot(player, now).ifPresent(value -> snapshots.put(player, value));
        links.clear();
        byPlayer.clear();
        names.clear();
        standalone.clear();
        return snapshots;
    }

    private Map<UUID, CombatSnapshot> inactiveSnapshots(Set<UUID> affected,
                                                        Map<UUID, List<OpponentSnapshot>> removed,
                                                        long now) {
        Map<UUID, CombatSnapshot> output = new LinkedHashMap<>();
        for (UUID playerId : affected) {
            if (isInCombat(playerId, now)) continue;
            List<OpponentSnapshot> opponents = removed.getOrDefault(playerId, List.of());
            if (opponents.isEmpty()) continue;
            String name = names.getOrDefault(playerId, playerId.toString());
            long expiry = 0L;
            double dealt = 0D;
            double taken = 0D;
            int hitsDealt = 0;
            int hitsTaken = 0;
            UUID lastAggressor = null;
            long lastHit = Long.MIN_VALUE;
            for (OpponentSnapshot opponent : opponents) {
                expiry = Math.max(expiry, opponent.expiresAt());
                dealt += opponent.damageDealt();
                taken += opponent.damageTaken();
                hitsDealt += opponent.hitsDealt();
                hitsTaken += opponent.hitsTaken();
                if (opponent.lastDamageTakenAt() > lastHit) {
                    lastHit = opponent.lastDamageTakenAt();
                    lastAggressor = opponent.playerId();
                }
            }
            output.put(playerId, new CombatSnapshot(playerId, name, now, expiry, opponents,
                    dealt, taken, hitsDealt, hitsTaken, lastAggressor));
            names.remove(playerId);
        }
        return output;
    }

    private void removeLink(CombatLink link) {
        CombatPair pair = link.pair();
        links.remove(pair);
        removeIndex(pair.first(), pair);
        removeIndex(pair.second(), pair);
    }

    private void removeIndex(UUID playerId, CombatPair pair) {
        Set<CombatPair> pairs = byPlayer.get(playerId);
        if (pairs == null) return;
        pairs.remove(pair);
        if (pairs.isEmpty()) byPlayer.remove(playerId);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
