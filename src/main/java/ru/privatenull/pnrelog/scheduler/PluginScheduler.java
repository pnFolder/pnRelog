package ru.privatenull.pnrelog.scheduler;

import org.bukkit.entity.Player;

public interface PluginScheduler extends ru.privatenull.pnrelog.api.scheduler.PnScheduler {

    void cancelAll();
}
