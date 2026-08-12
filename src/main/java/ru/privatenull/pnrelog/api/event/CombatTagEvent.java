package ru.privatenull.pnrelog.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnrelog.api.TagCause;

import java.util.Objects;

/** Called before a combat link is created or refreshed. */
public final class CombatTagEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private final Player target;
    private final TagCause cause;
    private final EntityDamageEvent.DamageCause damageCause;
    private final double effectiveDamage;
    private final boolean newLink;
    private long durationMillis;
    private boolean cancelled;

    public CombatTagEvent(Player attacker, Player target, TagCause cause,
                          @Nullable EntityDamageEvent.DamageCause damageCause, double effectiveDamage,
                          boolean newLink, long durationMillis) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.target = Objects.requireNonNull(target, "target");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.damageCause = damageCause;
        this.effectiveDamage = effectiveDamage;
        this.newLink = newLink;
        this.durationMillis = durationMillis;
    }

    public Player getAttacker() {
        return attacker;
    }

    public Player getTarget() {
        return target;
    }

    public TagCause getCause() {
        return cause;
    }

    @Nullable
    public EntityDamageEvent.DamageCause getDamageCause() {
        return damageCause;
    }

    public double getEffectiveDamage() {
        return effectiveDamage;
    }

    public boolean isNewLink() {
        return newLink;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("durationMillis must be positive");
        }
        this.durationMillis = durationMillis;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
