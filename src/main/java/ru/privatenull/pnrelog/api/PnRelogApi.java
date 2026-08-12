package ru.privatenull.pnrelog.api;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread API exposed through Bukkit's ServicesManager.
 */
public interface PnRelogApi {
    boolean isInCombat(UUID playerId);

    Optional<CombatSnapshot> getCombat(UUID playerId);

    Set<UUID> getTaggedPlayers();

    boolean tag(Player attacker, Player target, Duration duration, TagCause cause);

    boolean tag(Player player, Duration duration);

    boolean clear(UUID playerId);

    void grantLogoutPermit(UUID playerId, Duration lifetime, String source);

    boolean hasLogoutPermit(UUID playerId);
}
