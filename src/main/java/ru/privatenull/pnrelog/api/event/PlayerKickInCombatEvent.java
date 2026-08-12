package ru.privatenull.pnrelog.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.CombatSnapshot;

import java.util.Objects;

public final class PlayerKickInCombatEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CombatSnapshot combat;
    private final String reason;
    public PlayerKickInCombatEvent(CombatSnapshot combat, String reason) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.reason = reason == null ? "" : reason;
    }
    public CombatSnapshot getCombat() { return combat; }
    public String getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
