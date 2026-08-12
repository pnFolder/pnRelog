package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.TagCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

public final class CombatStartEvent extends CombatTransitionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public CombatStartEvent(Player attacker, Player target, TagCause cause,
                            @Nullable EntityDamageEvent.DamageCause damageCause,
                            double damage, long durationMillis) {
        super(attacker, target, cause, damageCause, damage, null, null, durationMillis);
    }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
