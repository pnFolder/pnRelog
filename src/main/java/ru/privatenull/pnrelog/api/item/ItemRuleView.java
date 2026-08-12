package ru.privatenull.pnrelog.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.Set;

public record ItemRuleView(
        String id,
        String displayName,
        ItemStack prototype,
        Set<String> interactions,
        Set<String> matchers,
        boolean prevention,
        long cooldownMillis
) {
    public ItemRuleView {
        prototype = prototype.clone();
        interactions = Set.copyOf(interactions);
        matchers = Set.copyOf(matchers);
    }
}
