package ru.privatenull.pnrelog.api.scheduler;

import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.scheduler.ScheduledHandle;

public interface PnScheduler {
    boolean isFolia();
    ScheduledHandle runGlobal(Runnable task);
    ScheduledHandle runGlobalLater(Runnable task, long delayTicks);
    ScheduledHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks);
    ScheduledHandle runEntity(Player player, Runnable task);
    ScheduledHandle runEntityLater(Player player, Runnable task, long delayTicks);
    ScheduledHandle runEntityTimer(Player player, Runnable task, long initialDelayTicks, long periodTicks);
}
