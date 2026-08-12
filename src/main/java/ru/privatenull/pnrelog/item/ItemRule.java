package ru.privatenull.pnrelog.item;

import org.bukkit.inventory.ItemStack;
import ru.privatenull.pnrelog.api.item.ItemRole;
import ru.privatenull.pnrelog.api.item.ItemRuleView;

import java.util.Set;

record ItemRule(
        String id,
        String displayName,
        ItemStack prototype,
        Set<String> interactions,
        Set<String> matchers,
        Set<ItemRole> roles,
        boolean prevention,
        long cooldownMillis,
        boolean materialCooldown,
        Set<String> linkedRules
) {
    ItemRule {
        prototype = prototype.clone();
        interactions = Set.copyOf(interactions);
        matchers = Set.copyOf(matchers);
        roles = Set.copyOf(roles);
        linkedRules = Set.copyOf(linkedRules);
    }

    ItemRuleView view() {
        return new ItemRuleView(id, displayName, prototype, interactions, matchers,
                prevention, cooldownMillis);
    }
}
