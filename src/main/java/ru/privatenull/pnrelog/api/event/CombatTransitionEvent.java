package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.TagCause;

import java.util.Objects;

public abstract class CombatTransitionEvent extends Event implements Cancellable {
    private final Player attacker;
    private final Player target;
    private final TagCause cause;
    private final EntityDamageEvent.DamageCause damageCause;
    private final double effectiveDamage;
    private final CombatSnapshot attackerBefore;
    private final CombatSnapshot targetBefore;
    private long durationMillis;
    private boolean cancelled;

    protected CombatTransitionEvent(Player attacker, Player target, TagCause cause,
                                    @Nullable EntityDamageEvent.DamageCause damageCause, double effectiveDamage,
                                    @Nullable CombatSnapshot attackerBefore,
                                    @Nullable CombatSnapshot targetBefore, long durationMillis) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.target = Objects.requireNonNull(target, "target");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.damageCause = damageCause;
        this.effectiveDamage = effectiveDamage;
        this.attackerBefore = attackerBefore;
        this.targetBefore = targetBefore;
        setDurationMillis(durationMillis);
    }

    public Player getAttacker() { return attacker; }
    public Player getTarget() { return target; }
    public TagCause getCause() { return cause; }
    @Nullable public EntityDamageEvent.DamageCause getDamageCause() { return damageCause; }
    public double getEffectiveDamage() { return effectiveDamage; }
    @Nullable public CombatSnapshot getAttackerBefore() { return attackerBefore; }
    @Nullable public CombatSnapshot getTargetBefore() { return targetBefore; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) {
        if (durationMillis <= 0L) throw new IllegalArgumentException("durationMillis must be positive");
        this.durationMillis = durationMillis;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
