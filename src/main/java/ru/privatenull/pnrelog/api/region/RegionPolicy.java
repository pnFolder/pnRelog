package ru.privatenull.pnrelog.api.region;

import org.bukkit.entity.Player;

/** Return true to let already tagged opponents continue combat in a protected region. */
@FunctionalInterface
public interface RegionPolicy {
    boolean allowContinuation(Player attacker, Player target);
}
