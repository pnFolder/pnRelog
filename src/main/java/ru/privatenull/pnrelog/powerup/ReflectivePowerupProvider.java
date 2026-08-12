package ru.privatenull.pnrelog.powerup;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pnrelog.api.powerup.PowerupAdapter;
import ru.privatenull.pnrelog.api.powerup.PowerupProvider;
import ru.privatenull.pnrelog.api.powerup.PowerupType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

final class ReflectivePowerupProvider implements PowerupProvider {
    private final Map<PowerupType, PowerupAdapter> adapters = new EnumMap<>(PowerupType.class);

    private ReflectivePowerupProvider() {
    }

    static ReflectivePowerupProvider essentials() throws ReflectiveOperationException {
        Plugin plugin = requiredPlugin("Essentials");
        Method getUser = find(plugin.getClass(), "getUser", Player.class);
        ReflectivePowerupProvider provider = common();
        provider.adapters.put(PowerupType.GOD, userAdapter(plugin, getUser,
                "isGodModeEnabled", "setGodModeEnabled"));
        provider.adapters.put(PowerupType.VANISH, userAdapter(plugin, getUser,
                "isVanished", "setVanished"));
        return provider;
    }

    static ReflectivePowerupProvider cmi() throws ReflectiveOperationException {
        Plugin plugin = requiredPlugin("CMI");
        Method getPlayerManager = find(plugin.getClass(), "getPlayerManager");
        Object playerManager = getPlayerManager.invoke(plugin);
        Method getUser = find(playerManager.getClass(), "getUser", Player.class);
        ReflectivePowerupProvider provider = common();
        provider.adapters.put(PowerupType.FLY, adapter(
                player -> bool(invokeUser(playerManager, getUser, player, "isFlying")),
                player -> invokeUser(playerManager, getUser, player, "setFlying", false)));
        provider.adapters.put(PowerupType.GOD, adapter(
                player -> bool(invokeUser(playerManager, getUser, player, "isGod")),
                player -> {
                    Object user = safeUser(playerManager, getUser, player);
                    invokeIfPresent(user, "setTgod", 0L);
                    invokeIfPresent(user, "setGod", false);
                    try {
                        Object nms = find(plugin.getClass(), "getNMS").invoke(plugin);
                        find(nms.getClass(), "changeGodMode", Player.class, boolean.class)
                                .invoke(nms, player, false);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }));
        provider.adapters.put(PowerupType.VANISH, adapter(
                player -> bool(invokeUser(playerManager, getUser, player, "isVanished")),
                player -> invokeUser(playerManager, getUser, player, "setVanished", false)));
        provider.adapters.put(PowerupType.GAMEMODE, adapter(
                player -> player.getGameMode() != GameMode.SURVIVAL,
                player -> {
                    Object user = safeUser(playerManager, getUser, player);
                    invokeIfPresent(user, "setGameMode", GameMode.SURVIVAL);
                    player.setGameMode(GameMode.SURVIVAL);
                }));
        return provider;
    }

    private static ReflectivePowerupProvider common() {
        ReflectivePowerupProvider provider = new ReflectivePowerupProvider();
        provider.adapters.put(PowerupType.FLY, adapter(Player::isFlying, player -> {
            player.setFlying(false);
            player.setAllowFlight(false);
        }));
        provider.adapters.put(PowerupType.GOD, adapter(Player::isInvulnerable,
                player -> player.setInvulnerable(false)));
        provider.adapters.put(PowerupType.VANISH, adapter(Player::isInvisible,
                player -> player.setInvisible(false)));
        provider.adapters.put(PowerupType.GAMEMODE, adapter(
                player -> player.getGameMode() != GameMode.SURVIVAL,
                player -> player.setGameMode(GameMode.SURVIVAL)));
        provider.adapters.put(PowerupType.WALKSPEED, adapter(
                player -> Math.abs(player.getWalkSpeed() - 0.2F) > 0.001F,
                player -> player.setWalkSpeed(0.2F)));
        return provider;
    }

    @Override public PowerupAdapter adapter(PowerupType type) { return adapters.get(type); }

    private static PowerupAdapter userAdapter(Object plugin, Method getUser,
                                              String checker, String setter) {
        return adapter(player -> bool(invokeUser(plugin, getUser, player, checker)),
                player -> invokeUser(plugin, getUser, player, setter, false));
    }

    private static Object invokeUser(Object owner, Method getUser, Player player,
                                     String method, Object... arguments) {
        try {
            Object user = user(owner, getUser, player);
            Method target = compatible(user.getClass(), method, arguments);
            return target.invoke(user, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object user(Object owner, Method getUser, Player player)
            throws InvocationTargetException, IllegalAccessException {
        return getUser.invoke(owner, player);
    }

    private static Object safeUser(Object owner, Method getUser, Player player) {
        try {
            return user(owner, getUser, player);
        } catch (InvocationTargetException | IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void invokeIfPresent(Object owner, String method, Object argument) {
        try {
            compatible(owner.getClass(), method, argument).invoke(owner, argument);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method compatible(Class<?> type, String name, Object... arguments)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            return method;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Method find(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        return type.getMethod(name, arguments);
    }

    private static Plugin requiredPlugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException(name + " is not enabled");
        return plugin;
    }

    private static boolean bool(Object value) { return value instanceof Boolean bool && bool; }

    private static PowerupAdapter adapter(java.util.function.Predicate<Player> checker,
                                          java.util.function.Consumer<Player> disabler) {
        return new PowerupAdapter() {
            @Override public boolean active(Player player) { return checker.test(player); }
            @Override public void disable(Player player) { disabler.accept(player); }
        };
    }
}
