package ru.privatenull.pnrelog.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Uses Folia schedulers without linking Folia-only classes, preserving Paper 1.16 compatibility.
 */
public final class PlatformScheduler implements PluginScheduler {
    private final Plugin plugin;
    private final boolean folia;
    private final Object globalScheduler;
    private final Set<ScheduledHandle> handles = ConcurrentHashMap.newKeySet();

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        Object scheduler = null;
        boolean detected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Method accessor = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler");
            scheduler = accessor.invoke(plugin.getServer());
            detected = scheduler != null;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            detected = false;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось инициализировать Folia scheduler", exception);
        }
        this.folia = detected;
        this.globalScheduler = scheduler;
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public ScheduledHandle runGlobal(Runnable task) {
        if (!folia) return track(Bukkit.getScheduler().runTask(plugin, task));
        return invokeScheduled(globalScheduler, "run", new Class<?>[]{Plugin.class, Consumer.class},
                plugin, consumer(task));
    }

    @Override
    public ScheduledHandle runGlobalLater(Runnable task, long delayTicks) {
        long safeDelay = Math.max(1L, delayTicks);
        if (!folia) return track(Bukkit.getScheduler().runTaskLater(plugin, task, safeDelay));
        return invokeScheduled(globalScheduler, "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, long.class},
                plugin, consumer(task), safeDelay);
    }

    @Override
    public ScheduledHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        long safeInitial = Math.max(1L, initialDelayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        if (!folia) return track(Bukkit.getScheduler().runTaskTimer(plugin, task, safeInitial, safePeriod));
        return invokeScheduled(globalScheduler, "runAtFixedRate",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                plugin, consumer(task), safeInitial, safePeriod);
    }

    @Override
    public ScheduledHandle runEntity(Player player, Runnable task) {
        if (!folia) return runGlobal(task);
        return invokeEntity(player, "run", task, 0L, 0L);
    }

    @Override
    public ScheduledHandle runEntityLater(Player player, Runnable task, long delayTicks) {
        if (!folia) return runGlobalLater(task, delayTicks);
        return invokeEntity(player, "runDelayed", task, Math.max(1L, delayTicks), 0L);
    }

    @Override
    public ScheduledHandle runEntityTimer(Player player, Runnable task, long initialDelayTicks, long periodTicks) {
        if (!folia) return runGlobalTimer(task, initialDelayTicks, periodTicks);
        return invokeEntity(player, "runAtFixedRate", task,
                Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    private ScheduledHandle invokeEntity(Player player, String methodName, Runnable task,
                                         long firstTicks, long periodTicks) {
        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Class<?>[] parameterTypes;
            Object[] arguments;
            if (methodName.equals("run")) {
                parameterTypes = new Class<?>[]{Plugin.class, Consumer.class, Runnable.class};
                arguments = new Object[]{plugin, consumer(task), null};
            } else if (methodName.equals("runDelayed")) {
                parameterTypes = new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class};
                arguments = new Object[]{plugin, consumer(task), null, firstTicks};
            } else {
                parameterTypes = new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class, long.class};
                arguments = new Object[]{plugin, consumer(task), null, firstTicks, periodTicks};
            }
            return invokeScheduled(entityScheduler, methodName, parameterTypes, arguments);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка EntityScheduler Folia для " + player.getName(), exception);
            return ScheduledHandle.NOOP;
        }
    }

    private ScheduledHandle invokeScheduled(Object scheduler, String methodName,
                                             Class<?>[] parameterTypes, Object... arguments) {
        try {
            Object scheduledTask = scheduler.getClass().getMethod(methodName, parameterTypes)
                    .invoke(scheduler, arguments);
            ReflectionHandle handle = new ReflectionHandle(scheduledTask);
            handles.add(handle);
            return handle;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка GlobalRegionScheduler Folia: " + methodName, exception);
            return ScheduledHandle.NOOP;
        }
    }

    private ScheduledHandle track(BukkitTask task) {
        ScheduledHandle handle = new ScheduledHandle() {
            @Override
            public void cancel() {
                task.cancel();
                handles.remove(this);
            }
        };
        handles.add(handle);
        return handle;
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    @Override
    public void cancelAll() {
        for (ScheduledHandle handle : Set.copyOf(handles)) handle.cancel();
        handles.clear();
        if (!folia) Bukkit.getScheduler().cancelTasks(plugin);
    }

    private final class ReflectionHandle implements ScheduledHandle {
        private final Object task;

        private ReflectionHandle(Object task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                plugin.getLogger().log(Level.WARNING, "Не удалось отменить Folia task", exception);
            } finally {
                handles.remove(this);
            }
        }
    }
}
