package ru.privatenull.pnrelog.listener;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;
import ru.privatenull.pnrelog.api.TagCause;
import ru.privatenull.pnrelog.config.PluginSettings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class DamageAttribution {
    record Attribution(Player player, TagCause cause) {
    }

    private record CrystalOwner(UUID playerId, long expiresAt) {
    }

    private final Map<UUID, CrystalOwner> crystalOwners = new HashMap<>();

    void rememberCrystal(EnderCrystal crystal, Player player, long now) {
        prune(now);
        crystalOwners.put(crystal.getUniqueId(), new CrystalOwner(player.getUniqueId(), now + 30_000L));
    }

    Attribution resolve(Entity source, PluginSettings.Combat settings, long now) {
        if (source instanceof Player player) return new Attribution(player, TagCause.MELEE);
        if (source instanceof Projectile projectile && settings.detectProjectiles()) {
            if (settings.ignoredProjectiles().contains(projectile.getType())) return null;
            Player owner = playerSource(projectile.getShooter());
            if (owner != null) return new Attribution(owner, TagCause.PROJECTILE);
        }
        if (source instanceof AreaEffectCloud cloud && settings.detectAreaEffects()) {
            Player owner = playerSource(cloud.getSource());
            if (owner != null) return new Attribution(owner, TagCause.AREA_EFFECT);
        }
        if (source instanceof TNTPrimed tnt && settings.detectExplosions()) {
            Entity owner = tnt.getSource();
            if (owner instanceof Player player) return new Attribution(player, TagCause.EXPLOSION);
            if (owner instanceof Tameable tameable && settings.detectPets()
                    && tameable.getOwner() instanceof Player player) {
                return new Attribution(player, TagCause.EXPLOSION);
            }
        }
        if (source instanceof EnderCrystal crystal && settings.detectExplosions()) {
            CrystalOwner owner = crystalOwners.get(crystal.getUniqueId());
            if (owner != null && owner.expiresAt() > now) {
                Player player = org.bukkit.Bukkit.getPlayer(owner.playerId());
                if (player != null) return new Attribution(player, TagCause.EXPLOSION);
            }
        }
        if (source instanceof Tameable tameable && settings.detectPets()
                && tameable.getOwner() instanceof Player player) {
            return new Attribution(player, TagCause.PET);
        }
        if (source instanceof EvokerFangs fangs && settings.detectPets()
                && fangs.getOwner() instanceof Player player) {
            return new Attribution(player, TagCause.PET);
        }
        return null;
    }

    void prune(long now) {
        Iterator<CrystalOwner> iterator = crystalOwners.values().iterator();
        while (iterator.hasNext()) if (iterator.next().expiresAt() <= now) iterator.remove();
    }

    private static Player playerSource(ProjectileSource source) {
        return source instanceof Player player ? player : null;
    }
}
