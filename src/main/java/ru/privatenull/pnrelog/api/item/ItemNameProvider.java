package ru.privatenull.pnrelog.api.item;

import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface ItemNameProvider {
    String name(ItemStack item);
}
