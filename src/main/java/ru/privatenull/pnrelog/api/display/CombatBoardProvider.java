package ru.privatenull.pnrelog.api.display;

import org.bukkit.entity.Player;

import java.util.List;

public interface CombatBoardProvider {
    void show(Player player, String title, List<String> lines);

    void hide(Player player);

    default void close() {
    }
}
