package ru.privatenull.pnrelog.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.restriction.CommandPolicy;
import ru.privatenull.pnrelog.text.MessageService;
import ru.privatenull.pnrelog.api.event.RestrictionDeniedEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RestrictionListener implements Listener {
    private final CombatService combat;
    private final MessageService messages;

    public RestrictionListener(CombatService combat, MessageService messages) {
        this.combat = combat;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("pnrelog.bypass.restrictions")) return;
        PluginSettings.Commands settings = combat.settings().restrictions().commands();
        String targetName = targetedCombatPlayer(event.getMessage(), settings);
        if (targetName != null) {
            event.setCancelled(true);
            messages.send(player, "player-command-blocked", Map.of("player", targetName));
            org.bukkit.Bukkit.getPluginManager().callEvent(new RestrictionDeniedEvent(player,
                    RestrictionDeniedEvent.Type.PLAYER_COMMAND, targetName, 0L));
            return;
        }
        if (!settings.enabled() || !combat.isInCombat(player.getUniqueId())) return;
        if (!CommandPolicy.blocked(event.getMessage(), settings)) return;
        event.setCancelled(true);
        long remaining = remainingMillis(player);
        messages.send(player, "command-blocked", Map.of("time", secondsCeil(remaining)));
        org.bukkit.Bukkit.getPluginManager().callEvent(new RestrictionDeniedEvent(player,
                RestrictionDeniedEvent.Type.COMMAND, event.getMessage(), remaining));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("pnrelog.bypass.restrictions")) return;
        PluginSettings.Commands settings = combat.settings().restrictions().commands();
        if (!settings.enabled() || !settings.filterTabComplete() || !combat.isInCombat(player.getUniqueId())) return;
        if (settings.mode() == PluginSettings.CommandMode.BLACKLIST) {
            Set<String> blockedRoots = new HashSet<>();
            for (String entry : settings.entries()) blockedRoots.add(CommandPolicy.root(entry));
            event.getCommands().removeIf(command -> blockedRoots.contains(CommandPolicy.root(command)));
        } else {
            Set<String> allowedRoots = new HashSet<>();
            for (String entry : settings.entries()) allowedRoots.add(CommandPolicy.root(entry));
            event.getCommands().retainAll(allowedRoots);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("pnrelog.bypass.restrictions")) return;
        PluginSettings.Teleports settings = combat.settings().restrictions().teleports();
        if (!settings.enabled() || !settings.blockedCauses().contains(event.getCause())) return;
        if (!combat.isInCombat(player.getUniqueId())) return;
        event.setCancelled(true);
        long remaining = remainingMillis(player);
        messages.send(player, "teleport-blocked", Map.of("time", secondsCeil(remaining)));
        org.bukkit.Bukkit.getPluginManager().callEvent(new RestrictionDeniedEvent(player,
                RestrictionDeniedEvent.Type.TELEPORT, event.getCause().name(), remaining));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !combat.settings().restrictions().blockElytra()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("pnrelog.bypass.restrictions")) return;
        if (player.getInventory().getChestplate() == null
                || player.getInventory().getChestplate().getType() != Material.ELYTRA) return;
        if (!combat.isInCombat(player.getUniqueId())) return;
        event.setCancelled(true);
        messages.send(player, "elytra-blocked");
        org.bukkit.Bukkit.getPluginManager().callEvent(new RestrictionDeniedEvent(player,
                RestrictionDeniedEvent.Type.ELYTRA, "ELYTRA", remainingMillis(player)));
    }

    private long remainingMillis(Player player) {
        Optional<CombatSnapshot> snapshot = combat.getCombat(player.getUniqueId());
        return snapshot.map(value -> value.remainingMillis(combat.now())).orElse(0L);
    }

    private static long secondsCeil(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    private String targetedCombatPlayer(String raw, PluginSettings.Commands settings) {
        String normalized = raw.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        for (String prefix : settings.targetingPrefixes()) {
            if (!(normalized.equals(prefix) || normalized.startsWith(prefix + " "))) continue;
            String rest = normalized.substring(prefix.length()).strip();
            if (rest.isEmpty()) continue;
            String name = rest.split(" ")[0];
            Player target = org.bukkit.Bukkit.getPlayerExact(name);
            if (target != null && combat.isInCombat(target.getUniqueId())) return target.getName();
        }
        return null;
    }
}
