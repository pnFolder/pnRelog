package ru.privatenull.pnrelog.logout;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.PnRelogPlugin;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.DisconnectKind;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.TagCause;
import ru.privatenull.pnrelog.api.event.CombatEscapeEvent;
import ru.privatenull.pnrelog.audit.AuditService;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.safety.DisconnectCircuitBreaker;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.scheduler.ScheduledHandle;
import ru.privatenull.pnrelog.text.MessageService;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LogoutService {
    private record PendingEscape(
            CombatSnapshot snapshot,
            DisconnectKind kind,
            String reason,
            long dueAt,
            ScheduledHandle task,
            boolean outageSuppressed
    ) {
    }

    private final PnRelogPlugin plugin;
    private final CombatService combat;
    private final MessageService messages;
    private final AuditService audit;
    private final PenaltyLedger ledger;
    private final DisconnectCircuitBreaker circuitBreaker;
    private final PluginScheduler scheduler;
    private final Map<UUID, PendingEscape> pending = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> handledKicks = new java.util.concurrent.ConcurrentHashMap<>();
    private PluginSettings settings;

    public LogoutService(PnRelogPlugin plugin, CombatService combat, MessageService messages,
                         AuditService audit, PluginSettings settings, PluginScheduler scheduler) {
        this.plugin = plugin;
        this.combat = combat;
        this.messages = messages;
        this.audit = audit;
        this.settings = settings;
        this.scheduler = scheduler;
        this.ledger = new PenaltyLedger(new File(plugin.getDataFolder(), "penalties.yml"), plugin.getLogger());
        this.circuitBreaker = new DisconnectCircuitBreaker(settings.safety().circuitBreaker());
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
        circuitBreaker.update(settings.safety().circuitBreaker());
    }

    public void handleKick(Player player, String rawReason) {
        long now = combat.now();
        handledKicks.put(player.getUniqueId(), now + 3_000L);
        String reason = ChatColor.stripColor(rawReason == null ? "" : rawReason);
        handleDisconnect(player, DisconnectKind.KICK, reason == null ? "" : reason);
    }

    public void handleQuit(Player player) {
        long now = combat.now();
        Long kickExpiry = handledKicks.remove(player.getUniqueId());
        handledKicks.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (kickExpiry != null && kickExpiry > now) return;
        handleDisconnect(player, DisconnectKind.QUIT, "");
    }

    private void handleDisconnect(Player player, DisconnectKind kind, String reason) {
        UUID playerId = player.getUniqueId();
        long now = combat.now();
        boolean opened = circuitBreaker.record(playerId, now, Bukkit.getOnlinePlayers().size());
        if (opened) {
            suppressCurrentWave();
            broadcast(messages.get("circuit-opened"));
            audit.record("CIRCUIT_OPENED", playerId, player.getName(),
                    "disconnects=" + circuitBreaker.recentDisconnects(now));
        }

        String permitSource = combat.consumeLogoutPermit(playerId);
        Optional<CombatSnapshot> optional = combat.getCombat(playerId);
        if (optional.isEmpty()) return;
        CombatSnapshot snapshot = optional.get();
        if (kind == DisconnectKind.KICK) {
            Bukkit.getPluginManager().callEvent(
                    new ru.privatenull.pnrelog.api.event.PlayerKickInCombatEvent(snapshot, reason));
        } else {
            Bukkit.getPluginManager().callEvent(
                    new ru.privatenull.pnrelog.api.event.PlayerLeaveInCombatEvent(snapshot));
        }

        String exemption = exemption(player, kind, reason, permitSource, now);
        if (exemption != null) {
            combat.clear(playerId, CombatEndReason.LOGOUT_EXEMPT);
            audit.record("ESCAPE_EXEMPT", playerId, player.getName(), exemption);
            return;
        }

        long dueAt = saturatingAdd(now, settings.logout().reconnectGraceMillis());
        ScheduledHandle task = schedule(playerId, dueAt, settings.logout().reconnectGraceMillis());
        PendingEscape previous = pending.put(playerId,
                new PendingEscape(snapshot, kind, reason, dueAt, task, false));
        if (previous != null) previous.task().cancel();
        if (settings.logout().penalty().kill()) {
            OpponentSnapshot opponent = selectOpponent(snapshot,
                    settings.logout().penalty().opponentSelection());
            ledger.put(new PenaltyDebt(playerId, snapshot.playerName(), System.currentTimeMillis(),
                    opponent == null ? null : opponent.playerId(),
                    opponent == null ? "" : opponent.playerName()));
        }
        audit.record("ESCAPE_PENDING", playerId, player.getName(),
                "kind=" + kind + ", grace_ms=" + settings.logout().reconnectGraceMillis());
    }

    private String exemption(Player player, DisconnectKind kind, String reason,
                             String permitSource, long now) {
        if (permitSource != null) return "permit=" + permitSource;
        if (player.hasPermission("pnrelog.bypass.logout")) return "permission";
        if (circuitBreaker.isOpen(now)) return "circuit-breaker";
        if (kind == DisconnectKind.QUIT && !settings.logout().punishQuits()) return "quit-policy";
        if (kind == DisconnectKind.KICK) {
            if (!settings.logout().punishKicks()) return "kick-policy";
            String normalized = reason.toLowerCase(Locale.ROOT);
            for (String ignored : settings.logout().ignoredKickReasons()) {
                if (normalized.contains(ignored)) return "kick-reason=" + ignored;
            }
        }
        return null;
    }

    public void handleJoin(Player player) {
        UUID playerId = player.getUniqueId();
        long now = combat.now();
        PendingEscape escape = pending.get(playerId);
        if (escape != null && now <= escape.dueAt()) {
            pending.remove(playerId);
            escape.task().cancel();
            ledger.remove(playerId);
            if (!combat.isInCombat(playerId)) restoreCombat(player, escape.snapshot());
            messages.send(player, "logout-forgiven");
            audit.record("ESCAPE_FORGIVEN", playerId, player.getName(), "reconnect-grace");
            return;
        }
        if (escape != null) {
            scheduler.runGlobal(() -> processPending(playerId, escape.dueAt()));
        }
        ledger.get(playerId).ifPresent(debt -> {
            long elapsed = Math.max(0L, System.currentTimeMillis() - debt.createdAt());
            if (elapsed <= settings.logout().reconnectGraceMillis()) {
                ledger.remove(playerId);
                messages.send(player, "logout-forgiven");
                audit.record("ESCAPE_FORGIVEN_AFTER_RESTART", playerId, player.getName(), "reconnect-grace");
            } else {
                messages.send(player, "pending-penalty");
                scheduler.runEntity(player, () -> applyDebt(player, debt));
            }
        });
    }

    private void restoreCombat(Player player, CombatSnapshot snapshot) {
        Duration duration = Duration.ofMillis(settings.logout().reconnectCombatMillis());
        for (OpponentSnapshot opponentSnapshot : snapshot.opponents()) {
            Player opponent = Bukkit.getPlayer(opponentSnapshot.playerId());
            if (opponent != null && opponent.isOnline()) {
                combat.tag(opponent, player, duration, TagCause.API);
            }
        }
    }

    private void processPending(UUID playerId, long dueAt) {
        PendingEscape escape = pending.get(playerId);
        if (escape == null || escape.dueAt() != dueAt) return;
        long now = combat.now();
        if (now < dueAt) {
            ScheduledHandle retry = schedule(playerId, dueAt, dueAt - now);
            pending.put(playerId, new PendingEscape(escape.snapshot(), escape.kind(), escape.reason(),
                    dueAt, retry, escape.outageSuppressed()));
            return;
        }
        pending.remove(playerId);
        if (escape.outageSuppressed() || circuitBreaker.isOpen(now)) {
            ledger.remove(playerId);
            combat.clear(playerId, CombatEndReason.LOGOUT_EXEMPT);
            audit.record("ESCAPE_SUPPRESSED", playerId, escape.snapshot().playerName(), "circuit-breaker");
            return;
        }

        PluginSettings.Penalty configured = settings.logout().penalty();
        OpponentSnapshot opponent = selectOpponent(escape.snapshot(), configured.opponentSelection());
        CombatEscapeEvent event = new CombatEscapeEvent(escape.snapshot(), escape.kind(), escape.reason(),
                opponent, configured.kill(), configured.broadcast(),
                configured.consoleCommands(), configured.opponentCommands());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            ledger.remove(playerId);
            combat.clear(playerId, CombatEndReason.LOGOUT_EXEMPT);
            audit.record("ESCAPE_CANCELLED", playerId, escape.snapshot().playerName(), "by-api");
            return;
        }

        if (event.willBroadcast()) {
            broadcast(messages.get("logout-broadcast", Map.of("player", escape.snapshot().playerName())));
        }
        runCommands(event.getConsoleCommands(), event.getOpponentCommands(), escape.snapshot(), opponent);

        Player online = Bukkit.getPlayer(playerId);
        if (event.willKillPlayer()) {
            if (online != null && online.isOnline()) {
                scheduler.runEntity(online, () -> kill(online));
                ledger.remove(playerId);
            } else {
                ledger.put(new PenaltyDebt(playerId, escape.snapshot().playerName(),
                        System.currentTimeMillis(), opponent == null ? null : opponent.playerId(),
                        opponent == null ? "" : opponent.playerName()));
            }
        } else ledger.remove(playerId);
        combat.clear(playerId, CombatEndReason.LOGOUT);
        audit.record("ESCAPE_PUNISHED", playerId, escape.snapshot().playerName(),
                "kill=" + event.willKillPlayer());
    }

    private void runCommands(List<String> consoleCommands, List<String> opponentCommands,
                             CombatSnapshot snapshot, OpponentSnapshot opponent) {
        for (String template : new ArrayList<>(consoleCommands)) {
            String command = renderCommand(template, snapshot, opponent);
            if (!command.isBlank()) scheduler.runGlobal(
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
        if (opponent == null) return;
        Player opponentPlayer = Bukkit.getPlayer(opponent.playerId());
        if (opponentPlayer == null || !opponentPlayer.isOnline()) return;
        for (String template : new ArrayList<>(opponentCommands)) {
            String command = renderCommand(template, snapshot, opponent);
            if (!command.isBlank()) scheduler.runEntity(opponentPlayer,
                    () -> opponentPlayer.performCommand(command));
        }
    }

    private static String renderCommand(String template, CombatSnapshot snapshot, OpponentSnapshot opponent) {
        if (template == null || template.indexOf('\n') >= 0 || template.indexOf('\r') >= 0) return "";
        String command = template.strip();
        if (command.startsWith("/")) command = command.substring(1);
        String opponentName = opponent == null ? "" : opponent.playerName();
        String opponentId = opponent == null ? "" : opponent.playerId().toString();
        return command
                .replace("{player}", snapshot.playerName())
                .replace("{uuid}", snapshot.playerId().toString())
                .replace("{opponent}", opponentName)
                .replace("{opponent_uuid}", opponentId)
                .replace("{damage_dealt}", String.format(Locale.US, "%.2f", snapshot.damageDealt()))
                .replace("{damage_taken}", String.format(Locale.US, "%.2f", snapshot.damageTaken()));
    }

    private static OpponentSnapshot selectOpponent(CombatSnapshot snapshot,
                                                    PluginSettings.OpponentSelection selection) {
        if (snapshot.opponents().isEmpty()) return null;
        if (selection == PluginSettings.OpponentSelection.LAST_AGGRESSOR
                && snapshot.lastAggressor() != null) {
            for (OpponentSnapshot opponent : snapshot.opponents()) {
                if (opponent.playerId().equals(snapshot.lastAggressor())) return opponent;
            }
        }
        return snapshot.opponents().stream()
                .max(Comparator.comparingDouble(OpponentSnapshot::damageTaken)
                        .thenComparingLong(OpponentSnapshot::lastHitAt))
                .orElse(snapshot.opponents().get(0));
    }

    private void applyDebt(Player player, PenaltyDebt debt) {
        if (!player.isOnline()) return;
        Optional<PenaltyDebt> current = ledger.get(player.getUniqueId());
        if (current.isEmpty() || current.get().createdAt() != debt.createdAt()) return;
        kill(player);
        ledger.remove(player.getUniqueId());
    }

    private static void kill(Player player) {
        if (!player.isDead() && player.getHealth() > 0D) player.setHealth(0D);
    }

    public int pendingPenaltyCount() {
        return pending.size() + ledger.size();
    }

    public boolean isCircuitOpen() {
        return circuitBreaker.isOpen(combat.now());
    }

    public long circuitRemainingMillis() {
        return circuitBreaker.remainingMillis(combat.now());
    }

    public void shutdown() {
        for (PendingEscape escape : pending.values()) escape.task().cancel();
        pending.clear();
        handledKicks.clear();
    }

    private void suppressCurrentWave() {
        for (Map.Entry<UUID, PendingEscape> entry : pending.entrySet()) {
            PendingEscape escape = entry.getValue();
            pending.replace(entry.getKey(), escape, new PendingEscape(escape.snapshot(), escape.kind(),
                    escape.reason(), escape.dueAt(), escape.task(), true));
            ledger.remove(entry.getKey());
        }
    }

    private ScheduledHandle schedule(UUID playerId, long dueAt, long remainingMillis) {
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L + 1L);
        return scheduler.runGlobalLater(() -> processPending(playerId, dueAt), delayTicks);
    }

    private void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
