package ru.privatenull.pnrelog.api.item;

import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ItemControlApi {
    void registerMetaMatcher(String id, MetaMatcher matcher);

    void setItemNameProvider(ItemNameProvider provider);

    <T extends Event> void registerInteraction(Class<T> eventClass, InteractionHandler<T> handler);

    Optional<ItemRuleView> findCooldownRule(ItemStack item);

    Optional<ItemRuleView> findPreventionRule(ItemStack item);

    long remainingCooldownMillis(UUID playerId, String ruleId);

    Set<String> registeredInteractions();

    Set<String> registeredMetaMatchers();
}
