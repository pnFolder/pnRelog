package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.TagCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

public final class CombatJoinEvent extends CombatTransitionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean attackerJoining;

    public CombatJoinEvent(Player attacker, Player target, TagCause cause,
                           @Nullable EntityDamageEvent.DamageCause damageCause, double damage,
                           CombatSnapshot attackerBefore, CombatSnapshot targetBefore,
                           boolean attackerJoining, long durationMillis) {
        super(attacker, target, cause, damageCause, damage, attackerBefore, targetBefore, durationMillis);
        this.attackerJoining = attackerJoining;
    }
    public boolean isAttackerJoining() { return attackerJoining; }
    public Player getJoiningPlayer() { return attackerJoining ? getAttacker() : getTarget(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
