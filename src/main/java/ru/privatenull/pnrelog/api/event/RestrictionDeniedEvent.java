package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class RestrictionDeniedEvent extends Event {
    public enum Type { COMMAND, PLAYER_COMMAND, TELEPORT, ELYTRA }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Type type;
    private final String value;
    private final long remainingMillis;

    public RestrictionDeniedEvent(Player player, Type type, String value, long remainingMillis) {
        this.player = Objects.requireNonNull(player, "player");
        this.type = Objects.requireNonNull(type, "type");
        this.value = value == null ? "" : value;
        this.remainingMillis = Math.max(0L, remainingMillis);
    }
    public Player getPlayer() { return player; }
    public Type getType() { return type; }
    public String getValue() { return value; }
    public long getRemainingMillis() { return remainingMillis; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
