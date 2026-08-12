package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.TagCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

public final class CombatContinueEvent extends CombatTransitionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public CombatContinueEvent(Player attacker, Player target, TagCause cause,
                               @Nullable EntityDamageEvent.DamageCause damageCause, double damage,
                               CombatSnapshot attackerBefore, CombatSnapshot targetBefore,
                               long durationMillis) {
        super(attacker, target, cause, damageCause, damage, attackerBefore, targetBefore, durationMillis);
    }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
