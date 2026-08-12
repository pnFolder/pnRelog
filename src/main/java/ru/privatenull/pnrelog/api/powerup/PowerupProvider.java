package ru.privatenull.pnrelog.api.powerup;

public interface PowerupProvider {
    PowerupAdapter adapter(PowerupType type);
}
