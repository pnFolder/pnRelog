package ru.privatenull.pnrelog.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.CombatSnapshot;

import java.util.Objects;

public final class PlayerLeaveInCombatEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CombatSnapshot combat;
    public PlayerLeaveInCombatEvent(CombatSnapshot combat) { this.combat = Objects.requireNonNull(combat, "combat"); }
    public CombatSnapshot getCombat() { return combat; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
