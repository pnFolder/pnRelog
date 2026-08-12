package ru.privatenull.pnrelog.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public record ObservedItem(ItemRole role, ItemStack item) {
    public ObservedItem {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(item, "item");
    }
}
