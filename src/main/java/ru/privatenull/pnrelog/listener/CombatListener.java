package ru.privatenull.pnrelog.listener;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.combat.CombatService;

public final class CombatListener implements Listener {
    private final CombatService combat;
    private final DamageAttribution attribution = new DamageAttribution();

    public CombatListener(CombatService combat) {
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        long now = combat.now();
        if (event.getEntity() instanceof EnderCrystal crystal) {
            DamageAttribution.Attribution source = attribution.resolve(event.getDamager(), combat.settings().combat(), now);
            if (source != null) attribution.rememberCrystal(crystal, source.player(), now);
            return;
        }
        if (!(event.getEntity() instanceof Player target)) return;
        double damage = event.getFinalDamage();
        if (!Double.isFinite(damage) || damage <= 0D
                || damage < combat.settings().combat().minimumEffectiveDamage()) return;
        DamageAttribution.Attribution source = attribution.resolve(event.getDamager(), combat.settings().combat(), now);
        if (source == null) return;
        combat.recordDamage(source.player(), target, damage, source.cause(), event.getCause());
        attribution.prune(now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        combat.clear(event.getEntity().getUniqueId(), CombatEndReason.DEATH);
    }
}
