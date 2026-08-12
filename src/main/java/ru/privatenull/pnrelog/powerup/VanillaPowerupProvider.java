package ru.privatenull.pnrelog.powerup;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.api.powerup.PowerupAdapter;
import ru.privatenull.pnrelog.api.powerup.PowerupProvider;
import ru.privatenull.pnrelog.api.powerup.PowerupType;

import java.util.EnumMap;
import java.util.Map;

final class VanillaPowerupProvider implements PowerupProvider {
    private final Map<PowerupType, PowerupAdapter> adapters = new EnumMap<>(PowerupType.class);

    VanillaPowerupProvider() {
        adapters.put(PowerupType.FLY, adapter(Player::isFlying, player -> {
            player.setFlying(false);
            player.setAllowFlight(false);
        }));
        adapters.put(PowerupType.GOD, adapter(Player::isInvulnerable, player -> player.setInvulnerable(false)));
        adapters.put(PowerupType.VANISH, adapter(Player::isInvisible, player -> player.setInvisible(false)));
        adapters.put(PowerupType.GAMEMODE, adapter(player -> player.getGameMode() != GameMode.SURVIVAL,
                player -> player.setGameMode(GameMode.SURVIVAL)));
        adapters.put(PowerupType.WALKSPEED, adapter(player -> Math.abs(player.getWalkSpeed() - 0.2F) > 0.001F,
                player -> player.setWalkSpeed(0.2F)));
    }

    @Override
    public PowerupAdapter adapter(PowerupType type) {
        return adapters.get(type);
    }

    private static PowerupAdapter adapter(java.util.function.Predicate<Player> checker,
                                          java.util.function.Consumer<Player> disabler) {
        return new PowerupAdapter() {
            @Override public boolean active(Player player) { return checker.test(player); }
            @Override public void disable(Player player) { disabler.accept(player); }
        };
    }
}
