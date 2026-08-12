package ru.privatenull.pnrelog.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.privatenull.pnrelog.logout.LogoutService;

public final class ConnectionListener implements Listener {
    private final LogoutService logout;

    public ConnectionListener(LogoutService logout) {
        this.logout = logout;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        logout.handleKick(event.getPlayer(), event.getReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        logout.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        logout.handleJoin(event.getPlayer());
    }
}
