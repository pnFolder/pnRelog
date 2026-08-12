package ru.privatenull.pnrelog.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.CombatSnapshot;

import java.util.Objects;
import java.util.UUID;

public final class CombatStateChangeEvent extends Event {
    public enum State { ENTERED, LEFT }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final State state;
    private final CombatEndReason reason;
    private final CombatSnapshot snapshot;

    public CombatStateChangeEvent(UUID playerId, String playerName, State state,
                                  @Nullable CombatEndReason reason, CombatSnapshot snapshot) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.state = Objects.requireNonNull(state, "state");
        this.reason = reason;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public State getState() {
        return state;
    }

    @Nullable
    public CombatEndReason getReason() {
        return reason;
    }

    public CombatSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
