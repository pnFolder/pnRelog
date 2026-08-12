package ru.privatenull.pnrelog.api.action;

import org.bukkit.entity.Player;

import java.util.List;

public interface ActionApi {
    void execute(ActionTrigger trigger, Player player, String... arguments);

    List<String> configuredActions(ActionTrigger trigger);
}
