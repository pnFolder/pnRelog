package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class ItemControlEvent extends Event {
    public enum Type { COOLDOWN_BLOCKED, PREVENTED, COOLDOWN_ENDED, HELD }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Type type;
    private final String ruleId;
    private final String itemName;
    private final long remainingMillis;

    public ItemControlEvent(Player player, Type type, String ruleId, String itemName, long remainingMillis) {
        this.player = Objects.requireNonNull(player, "player");
        this.type = Objects.requireNonNull(type, "type");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.itemName = Objects.requireNonNull(itemName, "itemName");
        this.remainingMillis = Math.max(0L, remainingMillis);
    }
    public Player getPlayer() { return player; }
    public Type getType() { return type; }
    public String getRuleId() { return ruleId; }
    public String getItemName() { return itemName; }
    public long getRemainingMillis() { return remainingMillis; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
