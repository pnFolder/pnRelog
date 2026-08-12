package ru.privatenull.pnrelog.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pnrelog.api.item.ItemRole;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ItemRuleLoader {
    record Rules(List<ItemRule> cooldowns, List<ItemRule> preventions) {
        Rules { cooldowns = List.copyOf(cooldowns); preventions = List.copyOf(preventions); }
    }

    private ItemRuleLoader() {
    }

    static Rules load(FileConfiguration config, MetaRegistry registry,
                      ru.privatenull.pnrelog.api.item.ItemNameProvider names) {
        List<ItemRule> cooldowns = loadSection(config.getConfigurationSection("items.cooldowns"), false, registry, names);
        List<ItemRule> preventions = loadSection(config.getConfigurationSection("items.preventions"), true, registry, names);
        Set<String> cooldownIds = new HashSet<>();
        for (ItemRule rule : cooldowns) {
            if (!cooldownIds.add(rule.id())) throw new IllegalArgumentException("Duplicate cooldown item id: " + rule.id());
        }
        for (ItemRule rule : cooldowns) {
            for (String linked : rule.linkedRules()) {
                if (!cooldownIds.contains(linked)) {
                    throw new IllegalArgumentException("Unknown linked cooldown item " + linked + " in " + rule.id());
                }
            }
        }
        return new Rules(cooldowns, preventions);
    }

    private static List<ItemRule> loadSection(ConfigurationSection section, boolean prevention,
                                              MetaRegistry registry,
                                              ru.privatenull.pnrelog.api.item.ItemNameProvider names) {
        if (section == null) return List.of();
        List<ItemRule> output = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection rule = section.getConfigurationSection(key);
            if (rule == null || !rule.getBoolean("enabled", true)) continue;
            String id = key.toLowerCase(Locale.ROOT);
            ItemStack prototype = prototype(rule, "items." + (prevention ? "preventions." : "cooldowns.") + key);
            Set<String> interactions = upperSet(rule.getStringList("interactions"));
            if (interactions.isEmpty()) throw new IllegalArgumentException("Item " + id + " has no interactions");
            Set<String> matchers = upperSet(rule.getStringList("matchers"));
            if (matchers.isEmpty()) matchers = Set.of("MATERIAL");
            Set<ItemRole> roles = new HashSet<>();
            List<String> roleValues = rule.getStringList("roles");
            if (roleValues.isEmpty()) roles.add(ItemRole.INTERACTED_ITEM);
            for (String role : roleValues) {
                try {
                    roles.add(ItemRole.valueOf(role.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown item role: " + role);
                }
            }
            int seconds = prevention ? 0 : rule.getInt("duration-seconds", 1);
            if (!prevention && (seconds < 1 || seconds > 86400)) {
                throw new IllegalArgumentException("duration-seconds for " + id + " must be 1..86400");
            }
            Set<String> linked = new HashSet<>();
            for (String value : rule.getStringList("linked")) linked.add(value.toLowerCase(Locale.ROOT));
            String configuredName = rule.getString("name", "");
            String displayName = configuredName == null || configuredName.isBlank()
                    ? names.name(prototype) : configuredName;
            output.add(new ItemRule(id, displayName, prototype, interactions, matchers, roles,
                    prevention, seconds * 1000L, rule.getBoolean("material-cooldown", false), linked));
        }
        return List.copyOf(output);
    }

    private static ItemStack prototype(ConfigurationSection section, String path) {
        String encoded = section.getString("item-base64", "").strip();
        if (!encoded.isEmpty()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + ".item-base64 is invalid", exception);
            }
        }
        String materialName = section.getString("material", "").toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException(path + ".material is invalid: " + materialName);
        }
        return new ItemStack(material);
    }

    private static Set<String> upperSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            String normalized = value.strip().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static String prettify(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return output.toString();
    }
}
