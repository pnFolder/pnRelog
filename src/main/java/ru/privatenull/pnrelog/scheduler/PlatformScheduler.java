package ru.privatenull.pnrelog.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import ru.privatenull.pnrelog.integration.Tasks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Планировщик плагина поверх задач pnLibrary ({@code ModuleContext.tasks}): поток выполнения выбирает библиотека
 * (глобальный или поток игрока на Folia), задачи отменяются вместе с модулем.
 */
public final class PlatformScheduler implements PluginScheduler {
    private final boolean folia;
    private final Set<TaskHandle> handles = ConcurrentHashMap.newKeySet();

    public PlatformScheduler(Plugin plugin) {
        boolean detected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException ignored) {
            detected = false;
        }
        this.folia = detected;
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public ScheduledHandle runGlobal(Runnable task) {
        return handle(Tasks.sync(task));
    }

    @Override
    public ScheduledHandle runGlobalLater(Runnable task, long delayTicks) {
        return handle(Tasks.syncLater(Math.max(1L, delayTicks), task));
    }

    @Override
    public ScheduledHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        return handle(Tasks.syncTimer(Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks), task));
    }

    @Override
    public ScheduledHandle runEntity(Player player, Runnable task) {
        return handle(Tasks.entity(player, task));
    }

    @Override
    public ScheduledHandle runEntityLater(Player player, Runnable task, long delayTicks) {
        return handle(Tasks.entityLater(player, Math.max(1L, delayTicks), task));
    }

    @Override
    public ScheduledHandle runEntityTimer(Player player, Runnable task, long initialDelayTicks, long periodTicks) {
        return handle(Tasks.entityTimer(player, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks), task));
    }

    /** Отменяет задачи этого планировщика; остальное отменит закрытие модуля pnLibrary. */
    @Override
    public void cancelAll() {
        for (TaskHandle task : Set.copyOf(handles)) task.cancel();
        handles.clear();
    }

    private ScheduledHandle handle(TaskHandle task) {
        if (task == null) return ScheduledHandle.NOOP;
        handles.add(task);
        return () -> {
            handles.remove(task);
            task.cancel();
        };
    }
}
