package ru.privatenull.pnrelog.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pnrelog.api.item.InteractionHandler;
import ru.privatenull.pnrelog.api.item.ItemRole;
import ru.privatenull.pnrelog.api.item.ObservedItem;

import java.util.ArrayList;
import java.util.List;

final class ItemInteractions {
    private ItemInteractions() {
    }

    static void register(ItemControlService service) {
        service.registerInteraction(EntityDamageByEntityEvent.class, new InteractionHandler<>(
                "SHIELD_BLOCK", event -> event.getEntity() instanceof Player player && player.isBlocking(),
                event -> (Player) event.getEntity(), event -> hands((Player) event.getEntity())));
        service.registerInteraction(PlayerItemConsumeEvent.class, new InteractionHandler<>(
                "CONSUME", event -> true, PlayerItemConsumeEvent::getPlayer,
                event -> item(event.getItem())));
        interact(service, "RIGHT_CLICK_AIR", Action.RIGHT_CLICK_AIR);
        interact(service, "RIGHT_CLICK_BLOCK", Action.RIGHT_CLICK_BLOCK);
        interact(service, "LEFT_CLICK_AIR", Action.LEFT_CLICK_AIR);
        interact(service, "LEFT_CLICK_BLOCK", Action.LEFT_CLICK_BLOCK);
        service.registerInteraction(BlockBreakEvent.class, new InteractionHandler<>(
                "BLOCK_BREAK", event -> true, BlockBreakEvent::getPlayer,
                event -> List.of(new ObservedItem(ItemRole.INTERACTED_ITEM,
                                event.getPlayer().getInventory().getItemInMainHand()),
                        new ObservedItem(ItemRole.INTERACTED_BLOCK,
                                new ItemStack(event.getBlock().getType())))));
        service.registerInteraction(EntityResurrectEvent.class, new InteractionHandler<>(
                "RESURRECT", event -> event.getEntity() instanceof Player,
                event -> (Player) event.getEntity(), event -> hands((Player) event.getEntity())));
        service.registerInteraction(EntityShootBowEvent.class, new InteractionHandler<>(
                "BOW_SHOOT", event -> event.getEntity() instanceof Player,
                event -> (Player) event.getEntity(), event -> item(event.getBow())));
        service.registerInteraction(ProjectileLaunchEvent.class, new InteractionHandler<>(
                "PROJECTILE_LAUNCH", event -> event.getEntity().getShooter() instanceof Player,
                event -> (Player) event.getEntity().getShooter(),
                event -> hands((Player) event.getEntity().getShooter())));
        service.registerInteraction(EntityDamageByEntityEvent.class, new InteractionHandler<>(
                "PLAYER_HIT_ENTITY", event -> event.getDamager() instanceof Player,
                event -> (Player) event.getDamager(),
                event -> item(((Player) event.getDamager()).getInventory().getItemInMainHand())));
        service.registerInteraction(EntityDamageByEntityEvent.class, new InteractionHandler<>(
                "PLAYER_HIT_PLAYER", event -> event.getDamager() instanceof Player
                        && event.getEntity() instanceof Player,
                event -> (Player) event.getDamager(),
                event -> item(((Player) event.getDamager()).getInventory().getItemInMainHand())));
        service.registerInteraction(EntityDamageByEntityEvent.class, new InteractionHandler<>(
                "PROJECTILE_HIT_PLAYER", event -> event.getEntity() instanceof Player
                        && event.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof Player,
                event -> (Player) ((Projectile) event.getDamager()).getShooter(),
                event -> projectileItem((Projectile) event.getDamager())));
        service.registerInteraction(PlayerRiptideEvent.class, new InteractionHandler<>(
                "RIPTIDE", event -> true, PlayerRiptideEvent::getPlayer,
                event -> item(event.getItem())));
        service.registerInteraction(PlayerFishEvent.class, new InteractionHandler<>(
                "FISHING", event -> true, PlayerFishEvent::getPlayer,
                event -> hands(event.getPlayer())));
    }

    private static void interact(ItemControlService service, String id, Action action) {
        service.registerInteraction(PlayerInteractEvent.class, new InteractionHandler<>(
                id, event -> event.getAction() == action, PlayerInteractEvent::getPlayer,
                event -> {
                    List<ObservedItem> items = new ArrayList<>();
                    if (event.getItem() != null) items.add(new ObservedItem(ItemRole.INTERACTED_ITEM, event.getItem()));
                    if (event.getClickedBlock() != null) items.add(new ObservedItem(ItemRole.INTERACTED_BLOCK,
                            new ItemStack(event.getClickedBlock().getType())));
                    return items;
                }));
    }

    private static List<ObservedItem> item(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return List.of();
        return List.of(new ObservedItem(ItemRole.INTERACTED_ITEM, item));
    }

    private static List<ObservedItem> hands(Player player) {
        List<ObservedItem> output = new ArrayList<>(2);
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (main != null && main.getType() != Material.AIR) output.add(new ObservedItem(ItemRole.INTERACTED_ITEM, main));
        if (off != null && off.getType() != Material.AIR) output.add(new ObservedItem(ItemRole.INTERACTED_ITEM, off));
        return output;
    }

    private static List<ObservedItem> projectileItem(Projectile projectile) {
        if (projectile instanceof Trident trident) return item(trident.getItemStack());
        return projectile.getShooter() instanceof Player player ? hands(player) : List.of();
    }
}
