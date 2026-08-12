package ru.privatenull.pnrelog.api.item;

import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface MetaMatcher {
    boolean matches(ItemStack configured, ItemStack actual);
}
