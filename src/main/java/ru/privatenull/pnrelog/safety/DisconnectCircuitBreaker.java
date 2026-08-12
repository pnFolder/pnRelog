package ru.privatenull.pnrelog.safety;

import ru.privatenull.pnrelog.config.PluginSettings;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Detects a likely proxy/server outage and temporarily suppresses logout sanctions. */
public final class DisconnectCircuitBreaker {
    private record Disconnect(UUID playerId, long at, int onlinePlayers) {
    }

    private final Deque<Disconnect> disconnects = new ArrayDeque<>();
    private final Map<UUID, Long> latestByPlayer = new HashMap<>();
    private PluginSettings.CircuitBreaker settings;
    private int peakOnline;
    private long openUntil;

    public DisconnectCircuitBreaker(PluginSettings.CircuitBreaker settings) {
        this.settings = settings;
    }

    public synchronized void update(PluginSettings.CircuitBreaker settings) {
        this.settings = settings;
        if (!settings.enabled()) {
            disconnects.clear();
            latestByPlayer.clear();
            peakOnline = 0;
            openUntil = 0L;
        }
    }

    /** @return true only when this call opens the circuit. */
    public synchronized boolean record(UUID playerId, long now, int onlinePlayers) {
        if (!settings.enabled()) return false;
        prune(now);
        Long previous = latestByPlayer.get(playerId);
        if (previous != null && now - previous <= settings.windowMillis()) return false;
        disconnects.addLast(new Disconnect(playerId, now, Math.max(onlinePlayers, 1)));
        latestByPlayer.put(playerId, now);
        peakOnline = Math.max(peakOnline, Math.max(onlinePlayers, disconnects.size()));
        if (isOpen(now)) return false;
        int threshold = Math.max(settings.minimumDisconnects(),
                (int) Math.ceil(peakOnline * settings.onlineFraction()));
        if (disconnects.size() < threshold) return false;
        openUntil = saturatingAdd(now, settings.openMillis());
        return true;
    }

    public synchronized boolean isOpen(long now) {
        return settings.enabled() && openUntil > now;
    }

    public synchronized long remainingMillis(long now) {
        return Math.max(0L, openUntil - now);
    }

    public synchronized int recentDisconnects(long now) {
        prune(now);
        return disconnects.size();
    }

    private void prune(long now) {
        long cutoff = now - settings.windowMillis();
        while (!disconnects.isEmpty() && disconnects.peekFirst().at() < cutoff) {
            Disconnect removed = disconnects.removeFirst();
            latestByPlayer.remove(removed.playerId(), removed.at());
        }
        peakOnline = 0;
        for (Disconnect disconnect : disconnects) peakOnline = Math.max(peakOnline, disconnect.onlinePlayers());
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
