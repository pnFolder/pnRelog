package ru.privatenull.pnrelog.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.PnRelogApi;
import ru.privatenull.pnrelog.api.TagCause;
import ru.privatenull.pnrelog.api.event.CombatStateChangeEvent;
import ru.privatenull.pnrelog.api.event.CombatTagEvent;
import ru.privatenull.pnrelog.api.event.CombatTransitionEvent;
import ru.privatenull.pnrelog.audit.AuditService;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.display.CombatDisplay;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.powerup.PowerupService;
import ru.privatenull.pnrelog.text.MessageService;
import ru.privatenull.pnrelog.util.TimeSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CombatService implements PnRelogApi {
    private final Plugin plugin;
    private final CombatGraph graph;
    private final TimeSource clock;
    private final MessageService messages;
    private final AuditService audit;
    private final CombatDisplay display;
    private final PluginScheduler scheduler;
    private final PowerupService powerups;
    private final Map<UUID, LogoutPermit> permits = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Map<UUID, CombatSnapshot> cachedSnapshots = Map.of();
    private PluginSettings settings;

    private record LogoutPermit(long expiresAt, String source) {
    }

    public CombatService(Plugin plugin, CombatGraph graph, TimeSource clock, PluginSettings settings,
                         MessageService messages, AuditService audit, CombatDisplay display,
                         PluginScheduler scheduler, PowerupService powerups) {
        this.plugin = plugin;
        this.graph = graph;
        this.clock = clock;
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.display = display;
        this.scheduler = scheduler;
        this.powerups = powerups;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
        display.updateSettings(settings);
    }

    public boolean recordDamage(Player attacker, Player target, double effectiveDamage, TagCause cause,
                                @Nullable EntityDamageEvent.DamageCause damageCause) {
        if (!Double.isFinite(effectiveDamage) || effectiveDamage <= 0D
                || effectiveDamage < settings.combat().minimumEffectiveDamage()) return false;
        return tag(attacker, target, settings.combat().durationMillis(), effectiveDamage, cause, damageCause);
    }

    @Override
    public boolean tag(Player attacker, Player target, Duration duration, TagCause cause) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        long durationMillis;
        try {
            durationMillis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("duration is too large", exception);
        }
        if (durationMillis <= 0L) throw new IllegalArgumentException("duration must be at least 1 ms");
        return tag(attacker, target, durationMillis, 0D, cause, null);
    }

    @Override
    public boolean tag(Player player, Duration duration) {
        if (player == null || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("player and positive duration are required");
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("duration is too large", exception);
        }
        if (millis <= 0L) throw new IllegalArgumentException("duration must be at least 1 ms");
        if (!eligible(player)) return false;
        long now = clock.now();
        finish(graph.prune(now), CombatEndReason.EXPIRED, true);
        boolean entered = graph.tagSingle(player.getUniqueId(), player.getName(), now, millis,
                settings.combat().maxLinkLifetimeMillis());
        if (entered) entered(player, "-", now);
        rebuildCache(now);
        return true;
    }

    private boolean tag(Player attacker, Player target, long durationMillis,
                        double effectiveDamage, TagCause cause,
                        @Nullable EntityDamageEvent.DamageCause damageCause) {
        if (attacker == null || target == null || cause == null) throw new IllegalArgumentException("Combat arguments cannot be null");
        if (attacker.getUniqueId().equals(target.getUniqueId())) return false;
        if (!eligible(attacker) || !eligible(target)) return false;
        if (effectiveDamage > 0D && effectiveDamage < settings.combat().minimumEffectiveDamage()) return false;

        long now = clock.now();
        finish(graph.prune(now), CombatEndReason.EXPIRED, true);
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        boolean attackerWasTagged = graph.isInCombat(attackerId, now);
        boolean targetWasTagged = graph.isInCombat(targetId, now);
        boolean newLink = !graph.hasLink(attackerId, targetId, now);
        CombatSnapshot attackerBefore = graph.snapshot(attackerId, now).orElse(null);
        CombatSnapshot targetBefore = graph.snapshot(targetId, now).orElse(null);
        if (newLink) {
            if (powerups.hasAny(attacker, settings.powerups().preventAttacker())
                    || powerups.hasAny(target, settings.powerups().preventTarget())) return false;
            ru.privatenull.pnrelog.api.event.CombatPreStartEvent preStart =
                    new ru.privatenull.pnrelog.api.event.CombatPreStartEvent(
                            attacker, target, cause, damageCause, effectiveDamage);
            Bukkit.getPluginManager().callEvent(preStart);
            if (preStart.isCancelled()) return false;
        }
        CombatTagEvent event = new CombatTagEvent(attacker, target, cause, damageCause, effectiveDamage,
                newLink, durationMillis);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        CombatTransitionEvent transition;
        if (!newLink) {
            transition = new ru.privatenull.pnrelog.api.event.CombatContinueEvent(
                    attacker, target, cause, damageCause, effectiveDamage, attackerBefore, targetBefore,
                    event.getDurationMillis());
        } else if (!attackerWasTagged && !targetWasTagged) {
            transition = new ru.privatenull.pnrelog.api.event.CombatStartEvent(
                    attacker, target, cause, damageCause, effectiveDamage, event.getDurationMillis());
        } else if (attackerWasTagged && targetWasTagged) {
            transition = new ru.privatenull.pnrelog.api.event.CombatMergeEvent(
                    attacker, target, cause, damageCause, effectiveDamage, attackerBefore, targetBefore,
                    event.getDurationMillis());
        } else {
            transition = new ru.privatenull.pnrelog.api.event.CombatJoinEvent(
                    attacker, target, cause, damageCause, effectiveDamage, attackerBefore, targetBefore,
                    !attackerWasTagged, event.getDurationMillis());
        }
        Bukkit.getPluginManager().callEvent(transition);
        if (transition.isCancelled()) return false;

        boolean created = graph.tag(attackerId, attacker.getName(), targetId, target.getName(), now,
                transition.getDurationMillis(), settings.combat().maxLinkLifetimeMillis(), effectiveDamage, cause);
        if (!attackerWasTagged) entered(attacker, target.getName(), now);
        if (!targetWasTagged) entered(target, attacker.getName(), now);
        if (newLink) {
            powerups.disable(attacker, settings.powerups().disableAttacker());
            powerups.disable(target, settings.powerups().disableTarget());
        }
        if (created) {
            audit.record("COMBAT_LINK_CREATED", attackerId, attacker.getName(),
                    "opponent=" + target.getName() + ", cause=" + cause);
        }
        rebuildCache(now);
        return true;
    }

    private boolean eligible(Player player) {
        if (player.hasPermission("pnrelog.bypass.tag")) return false;
        if (settings.combat().ignoredWorlds().contains(player.getWorld().getName().toLowerCase(java.util.Locale.ROOT))) return false;
        return !settings.combat().ignoredGameModes().contains(player.getGameMode());
    }

    private void entered(Player player, String opponent, long now) {
        messages.send(player, "combat-start", Map.of("opponent", opponent));
        if (settings.restrictions().blockElytra() && player.isGliding()
                && !player.hasPermission("pnrelog.bypass.restrictions")) {
            player.setGliding(false);
        }
        player.updateCommands();
        graph.snapshot(player.getUniqueId(), now).ifPresent(snapshot -> Bukkit.getPluginManager().callEvent(
                new CombatStateChangeEvent(player.getUniqueId(), player.getName(),
                        CombatStateChangeEvent.State.ENTERED, null, snapshot)));
    }

    @Override
    public boolean isInCombat(UUID playerId) {
        return graph.isInCombat(playerId, clock.now());
    }

    @Override
    public Optional<CombatSnapshot> getCombat(UUID playerId) {
        return graph.snapshot(playerId, clock.now());
    }

    @Override
    public Set<UUID> getTaggedPlayers() {
        return graph.taggedPlayers(clock.now());
    }

    @Override
    public boolean clear(UUID playerId) {
        return clear(playerId, CombatEndReason.API);
    }

    public boolean clear(UUID playerId, CombatEndReason reason) {
        long now = clock.now();
        finish(graph.prune(now), CombatEndReason.EXPIRED, true);
        if (!graph.isInCombat(playerId, now)) return false;
        Map<UUID, CombatSnapshot> ended = graph.removePlayer(playerId, now);
        finish(ended, reason, reason != CombatEndReason.LOGOUT && reason != CombatEndReason.LOGOUT_EXEMPT
                && reason != CombatEndReason.SHUTDOWN);
        rebuildCache(now);
        return true;
    }

    public void clearAll(CombatEndReason reason) {
        finish(graph.clear(clock.now()), reason, reason != CombatEndReason.SHUTDOWN);
        cachedSnapshots = Map.of();
    }

    public int activeLinkCount() {
        return graph.activeLinkCount(clock.now());
    }

    public void tick() {
        long now = clock.now();
        finish(graph.prune(now), CombatEndReason.EXPIRED, true);
        for (UUID playerId : graph.taggedPlayers(now)) {
            Player player = Bukkit.getPlayer(playerId);
            Optional<CombatSnapshot> snapshot = graph.snapshot(playerId, now);
            if (player != null && snapshot.isPresent()) {
                scheduler.runEntity(player, () -> {
                    display.show(player, snapshot.get(), clock.now());
                    Bukkit.getPluginManager().callEvent(
                            new ru.privatenull.pnrelog.api.event.CombatTickEvent(snapshot.get()));
                });
            }
        }
        permits.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        rebuildCache(now);
    }

    /** Thread-safe read intended for placeholder providers. */
    public Optional<CombatSnapshot> getCachedCombat(UUID playerId) {
        return Optional.ofNullable(cachedSnapshots.get(playerId));
    }

    private void finish(Map<UUID, CombatSnapshot> ended, CombatEndReason reason, boolean notifyPlayer) {
        for (Map.Entry<UUID, CombatSnapshot> entry : ended.entrySet()) {
            UUID playerId = entry.getKey();
            CombatSnapshot snapshot = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            String playerName = player == null ? snapshot.playerName() : player.getName();
            if (player != null) {
                scheduler.runEntity(player, () -> {
                    display.hide(playerId);
                    if (notifyPlayer) messages.send(player, "combat-end");
                    Bukkit.getPluginManager().callEvent(new CombatStateChangeEvent(playerId, playerName,
                            CombatStateChangeEvent.State.LEFT, reason, snapshot));
                    Bukkit.getPluginManager().callEvent(
                            new ru.privatenull.pnrelog.api.event.CombatEndEvent(snapshot, reason));
                    player.updateCommands();
                });
            } else {
                scheduler.runGlobal(() -> Bukkit.getPluginManager().callEvent(new CombatStateChangeEvent(
                        playerId, playerName, CombatStateChangeEvent.State.LEFT, reason, snapshot)));
                scheduler.runGlobal(() -> Bukkit.getPluginManager().callEvent(
                        new ru.privatenull.pnrelog.api.event.CombatEndEvent(snapshot, reason)));
            }
            audit.record("COMBAT_ENDED", playerId, playerName, "reason=" + reason);
        }
    }

    @Override
    public void grantLogoutPermit(UUID playerId, Duration lifetime, String source) {
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
        long lifetimeMillis;
        try {
            lifetimeMillis = lifetime.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("lifetime is too large", exception);
        }
        if (lifetimeMillis <= 0L) throw new IllegalArgumentException("lifetime must be at least 1 ms");
        String safeSource = source == null || source.isBlank() ? "api" : source;
        permits.put(playerId, new LogoutPermit(saturatingAdd(clock.now(), lifetimeMillis), safeSource));
        audit.record("LOGOUT_PERMIT_GRANTED", playerId, playerName(playerId), "source=" + safeSource);
    }

    @Override
    public boolean hasLogoutPermit(UUID playerId) {
        LogoutPermit permit = permits.get(playerId);
        if (permit == null) return false;
        if (permit.expiresAt() <= clock.now()) {
            permits.remove(playerId);
            return false;
        }
        return true;
    }

    public String consumeLogoutPermit(UUID playerId) {
        LogoutPermit permit = permits.remove(playerId);
        if (permit == null || permit.expiresAt() <= clock.now()) return null;
        return permit.source();
    }

    public long now() {
        return clock.now();
    }

    public PluginSettings settings() {
        return settings;
    }

    private String playerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? playerId.toString() : player.getName();
    }

    private void rebuildCache(long now) {
        Map<UUID, CombatSnapshot> rebuilt = new HashMap<>();
        for (UUID playerId : graph.taggedPlayers(now)) {
            graph.snapshot(playerId, now).ifPresent(snapshot -> rebuilt.put(playerId, snapshot));
        }
        cachedSnapshots = Map.copyOf(rebuilt);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
