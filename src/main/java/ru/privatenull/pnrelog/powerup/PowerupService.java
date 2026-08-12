package ru.privatenull.pnrelog.powerup;

import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.api.powerup.PowerupAdapter;
import ru.privatenull.pnrelog.api.powerup.PowerupApi;
import ru.privatenull.pnrelog.api.powerup.PowerupProvider;
import ru.privatenull.pnrelog.api.powerup.PowerupType;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;

import java.util.Objects;
import java.util.Set;

public final class PowerupService implements PowerupApi {
    private final PluginScheduler scheduler;
    private final java.util.logging.Logger logger;
    private volatile PowerupProvider provider = new VanillaPowerupProvider();

    public PowerupService(PluginScheduler scheduler, java.util.logging.Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void configure(String requested) {
        String normalized = requested == null ? "AUTO" : requested.toUpperCase(java.util.Locale.ROOT);
        try {
            if ((normalized.equals("AUTO") || normalized.equals("CMI"))
                    && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("CMI")) {
                setProvider(ReflectivePowerupProvider.cmi());
                logger.info("Powerup provider: CMI");
                return;
            }
            if ((normalized.equals("AUTO") || normalized.equals("ESSENTIALS"))
                    && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
                setProvider(ReflectivePowerupProvider.essentials());
                logger.info("Powerup provider: Essentials");
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("Powerup provider " + normalized + " недоступен: " + exception.getMessage());
        }
        setProvider(new VanillaPowerupProvider());
        logger.info("Powerup provider: Bukkit");
    }

    @Override
    public void setProvider(PowerupProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        for (PowerupType type : PowerupType.values()) {
            if (provider.adapter(type) == null) throw new IllegalArgumentException("Missing adapter for " + type);
        }
    }

    @Override
    public PowerupProvider getProvider() {
        return provider;
    }

    @Override
    public boolean hasAny(Player player, Set<PowerupType> types) {
        for (PowerupType type : types) {
            PowerupAdapter adapter = provider.adapter(type);
            if (adapter != null && adapter.active(player)) return true;
        }
        return false;
    }

    @Override
    public void disable(Player player, Set<PowerupType> types) {
        if (player.hasPermission("pnrelog.bypass.powerups")) return;
        scheduler.runEntity(player, () -> {
            for (PowerupType type : types) {
                PowerupAdapter adapter = provider.adapter(type);
                if (adapter != null && adapter.active(player)) adapter.disable(player);
            }
        });
    }
}
