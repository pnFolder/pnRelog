package ru.privatenull.pnrelog.api.powerup;

import org.bukkit.entity.Player;

import java.util.Set;

public interface PowerupApi {
    void setProvider(PowerupProvider provider);

    PowerupProvider getProvider();

    boolean hasAny(Player player, Set<PowerupType> types);

    void disable(Player player, Set<PowerupType> types);
}
