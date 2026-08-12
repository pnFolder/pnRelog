package ru.privatenull.pnrelog.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.DisconnectKind;
import ru.privatenull.pnrelog.api.OpponentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Allows integrations to suppress or replace the configured logout penalty. */
public final class CombatEscapeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final CombatSnapshot combat;
    private final DisconnectKind disconnectKind;
    private final String disconnectReason;
    private final OpponentSnapshot primaryOpponent;
    private final List<String> consoleCommands;
    private final List<String> opponentCommands;
    private boolean killPlayer;
    private boolean broadcast;
    private boolean cancelled;

    public CombatEscapeEvent(CombatSnapshot combat, DisconnectKind disconnectKind,
                             String disconnectReason, @Nullable OpponentSnapshot primaryOpponent,
                             boolean killPlayer, boolean broadcast, List<String> consoleCommands,
                             List<String> opponentCommands) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.disconnectKind = Objects.requireNonNull(disconnectKind, "disconnectKind");
        this.disconnectReason = disconnectReason == null ? "" : disconnectReason;
        this.primaryOpponent = primaryOpponent;
        this.killPlayer = killPlayer;
        this.broadcast = broadcast;
        this.consoleCommands = new ArrayList<>(consoleCommands);
        this.opponentCommands = new ArrayList<>(opponentCommands);
    }

    public CombatSnapshot getCombat() {
        return combat;
    }

    public DisconnectKind getDisconnectKind() {
        return disconnectKind;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    @Nullable
    public OpponentSnapshot getPrimaryOpponent() {
        return primaryOpponent;
    }

    public boolean willKillPlayer() {
        return killPlayer;
    }

    public void setKillPlayer(boolean killPlayer) {
        this.killPlayer = killPlayer;
    }

    public boolean willBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public List<String> getConsoleCommands() {
        return consoleCommands;
    }

    public List<String> getOpponentCommands() {
        return opponentCommands;
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
