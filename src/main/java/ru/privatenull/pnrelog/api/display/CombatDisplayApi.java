package ru.privatenull.pnrelog.api.display;

public interface CombatDisplayApi {
    void setScoreboardProvider(CombatBoardProvider provider);

    CombatBoardProvider getScoreboardProvider();
}
