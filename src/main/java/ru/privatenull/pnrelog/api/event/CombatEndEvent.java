package ru.privatenull.pnrelog.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.CombatSnapshot;

import java.util.Objects;

public final class CombatEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CombatSnapshot combat;
    private final CombatEndReason reason;
    public CombatEndEvent(CombatSnapshot combat, CombatEndReason reason) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.reason = Objects.requireNonNull(reason, "reason");
    }
    public CombatSnapshot getCombat() { return combat; }
    public CombatEndReason getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
