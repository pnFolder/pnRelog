package ru.privatenull.pnrelog.api.powerup;

import org.bukkit.entity.Player;

public interface PowerupAdapter {
    boolean active(Player player);

    void disable(Player player);
}
