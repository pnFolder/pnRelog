package ru.privatenull.pnrelog.logout;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PenaltyLedger {
    private final File file;
    private final Logger logger;
    private final Map<UUID, PenaltyDebt> debts = new HashMap<>();

    PenaltyLedger(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    synchronized Optional<PenaltyDebt> get(UUID playerId) {
        return Optional.ofNullable(debts.get(playerId));
    }

    synchronized void put(PenaltyDebt debt) {
        debts.put(debt.playerId(), debt);
        save();
    }

    synchronized void remove(UUID playerId) {
        if (debts.remove(playerId) != null) save();
    }

    synchronized int size() {
        return debts.size();
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("debts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String root = "debts." + key;
                String playerName = yaml.getString(root + ".player-name", playerId.toString());
                long createdAt = yaml.getLong(root + ".created-at");
                String opponentRaw = yaml.getString(root + ".opponent-id", "");
                UUID opponentId = opponentRaw.isBlank() ? null : UUID.fromString(opponentRaw);
                String opponentName = yaml.getString(root + ".opponent-name", "");
                debts.put(playerId, new PenaltyDebt(playerId, playerName, createdAt, opponentId, opponentName));
            } catch (IllegalArgumentException exception) {
                logger.warning("Пропущена повреждённая запись penalties.yml: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PenaltyDebt debt : debts.values()) {
            String root = "debts." + debt.playerId();
            yaml.set(root + ".player-name", debt.playerName());
            yaml.set(root + ".created-at", debt.createdAt());
            yaml.set(root + ".opponent-id", debt.opponentId() == null ? "" : debt.opponentId().toString());
            yaml.set(root + ".opponent-name", debt.opponentName());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Не удалось сохранить penalties.yml", exception);
        }
    }
}
