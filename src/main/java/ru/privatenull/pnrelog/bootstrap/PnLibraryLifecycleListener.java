package ru.privatenull.pnrelog.bootstrap;

import ru.privatenull.pnrelog.integration.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import ru.privatenull.pnrelog.PnRelogPlugin;

/** Keeps pnRelog from outliving the shared runtime during shutdown or manual unload. */
public final class PnLibraryLifecycleListener implements Listener {

    private final PnRelogPlugin plugin;

    public PnLibraryLifecycleListener(PnRelogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("pnLibrary") || !plugin.isEnabled()) return;
        Log.warning("pnLibrary отключается; pnRelog будет безопасно остановлен первым.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }
}
