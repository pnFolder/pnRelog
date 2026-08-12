package ru.privatenull.pnrelog.region;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.event.WrappedDisallowedPVPEvent;
import ru.privatenull.pnrelog.PnRelogPlugin;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.region.RegionApi;
import ru.privatenull.pnrelog.api.region.RegionPolicy;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.config.PluginSettings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

public final class RegionService implements RegionApi, Listener {
    private final PnRelogPlugin plugin;
    private final CombatService combat;
    private final java.util.logging.Logger logger;
    private volatile PluginSettings.Regions settings;
    private volatile RegionPolicy policy;
    private volatile String provider = "NONE";
    private volatile Object landsApi;

    public RegionService(PnRelogPlugin plugin, CombatService combat, PluginSettings.Regions settings,
                         java.util.logging.Logger logger) {
        this.plugin = plugin;
        this.combat = combat;
        this.settings = settings;
        this.logger = logger;
        detect();
    }

    public void update(PluginSettings.Regions settings) {
        this.settings = settings;
        if (!"CUSTOM".equals(provider)) detect();
    }

    @Override
    public void setPolicy(RegionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.provider = "CUSTOM";
    }

    @Override public RegionPolicy getPolicy() { return policy; }
    @Override public String activeProvider() { return provider; }

    /** Custom policies operate on the final Bukkit damage decision. Built-in hooks use native events below. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCustomProtectedDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() || !settings.enabled() || !"CUSTOM".equals(provider)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        Player attacker = DamagePlayers.attacker(event.getDamager());
        if (attacker == null || !eligiblePair(attacker, target)) return;
        RegionPolicy current = policy;
        if (current != null && current.allowContinuation(attacker, target)) event.setCancelled(false);
    }

    @EventHandler
    public void onWorldGuard(WrappedDisallowedPVPEvent event) {
        if (!settings.enabled() || !"WORLDGUARD".equals(provider)) return;
        Player attacker = event.getAttacker();
        Player target = event.getDefender();
        if (!eligiblePair(attacker, target)) return;
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    private void detect() {
        policy = null;
        landsApi = null;
        if (!settings.enabled()) {
            provider = "NONE";
            return;
        }
        String requested = settings.provider();
        try {
            if ((requested.equals("AUTO") || requested.equals("WORLDGUARD"))
                    && Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
                WorldGuardWrapper.getInstance().registerEvents(plugin);
                provider = "WORLDGUARD";
                logger.info("Region provider: WorldGuard");
                return;
            }
            if ((requested.equals("AUTO") || requested.equals("TOWNY"))
                    && Bukkit.getPluginManager().isPluginEnabled("Towny")) {
                registerDynamic("com.palmergames.bukkit.towny.event.damage.TownyPlayerDamagePlayerEvent",
                        this::handleTowny);
                provider = "TOWNY";
                logger.info("Region provider: Towny");
                return;
            }
            if ((requested.equals("AUTO") || requested.equals("LANDS"))
                    && Bukkit.getPluginManager().isPluginEnabled("Lands")) {
                Class<?> integration = Class.forName("me.angeschossen.lands.api.LandsIntegration");
                landsApi = integration.getMethod("of", org.bukkit.plugin.Plugin.class).invoke(null, plugin);
                registerDynamic("me.angeschossen.lands.api.events.player.area.PlayerAreaEnterEvent",
                        this::handleLands);
                provider = "LANDS";
                logger.info("Region provider: Lands");
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("Region provider " + requested + " не загружен: " + exception.getMessage());
        }
        provider = "NONE";
        logger.warning("Region provider " + requested + " не найден; continuation отключён");
    }

    @SuppressWarnings("unchecked")
    private void registerDynamic(String className, java.util.function.Consumer<Event> consumer)
            throws ClassNotFoundException {
        Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
        EventExecutor executor = (listener, event) -> {
            if (eventClass.isInstance(event)) consumer.accept(event);
        };
        Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST,
                executor, plugin, false);
    }

    private void handleTowny(Event event) {
        try {
            if (!booleanValue(invoke(event, "isCancelled"))) return;
            Player attacker = (Player) invoke(event, "getAttackingPlayer");
            Player target = (Player) invoke(event, "getVictimPlayer");
            if (eligiblePair(attacker, target)) invoke(event, "setCancelled", false);
        } catch (ReflectiveOperationException exception) {
            logger.warning("Towny region event error: " + exception.getMessage());
        }
    }

    private void handleLands(Event event) {
        try {
            Object landPlayer = invoke(event, "getLandPlayer");
            Player player = (Player) invoke(landPlayer, "getPlayer");
            if (!worldEnabled(player) || !combat.isInCombat(player.getUniqueId())) return;
            CombatSnapshot snapshot = combat.getCombat(player.getUniqueId()).orElse(null);
            if (snapshot == null) return;
            Object area = invoke(event, "getArea");
            for (OpponentSnapshot opponent : snapshot.opponents()) {
                Object opponentLandPlayer = invoke(landsApi, "getLandPlayer", opponent.playerId());
                if (opponentLandPlayer == null) continue;
                Object allowed = invoke(area, "canPvP", landPlayer, opponentLandPlayer, false);
                if (!booleanValue(allowed)) {
                    invoke(event, "setCancelled", true);
                    return;
                }
            }
        } catch (ReflectiveOperationException exception) {
            logger.warning("Lands region event error: " + exception.getMessage());
        }
    }

    private boolean eligiblePair(Player attacker, Player target) {
        return attacker != null && target != null && !attacker.equals(target)
                && worldEnabled(attacker)
                && combat.isInCombat(attacker.getUniqueId())
                && combat.isInCombat(target.getUniqueId());
    }

    private boolean worldEnabled(Player player) {
        return settings.worlds().isEmpty()
                || settings.worlds().contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private static Object invoke(Object owner, String method, Object... arguments)
            throws ReflectiveOperationException {
        for (Method candidate : owner.getClass().getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length) {
                try {
                    return candidate.invoke(owner, arguments);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "#" + method);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static final class DamagePlayers {
        private static Player attacker(org.bukkit.entity.Entity entity) {
            if (entity instanceof Player player) return player;
            if (entity instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Player player) return player;
            return null;
        }
    }
}
