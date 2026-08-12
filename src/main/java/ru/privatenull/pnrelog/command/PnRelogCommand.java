package ru.privatenull.pnrelog.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.PnRelogPlugin;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.CombatSnapshot;
import ru.privatenull.pnrelog.api.OpponentSnapshot;
import ru.privatenull.pnrelog.api.TagCause;
import ru.privatenull.pnrelog.audit.AuditRecord;
import ru.privatenull.pnrelog.audit.AuditService;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.logout.LogoutService;
import ru.privatenull.pnrelog.item.ItemControlService;
import ru.privatenull.pnrelog.update.UpdateService;
import ru.privatenull.pnrelog.text.MessageService;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PnRelogCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PnRelogPlugin plugin;
    private final CombatService combat;
    private final LogoutService logout;
    private final MessageService messages;
    private final AuditService audit;
    private final ItemControlService items;
    private final UpdateService updates;

    public PnRelogCommand(PnRelogPlugin plugin, CombatService combat, LogoutService logout,
                           MessageService messages, AuditService audit, ItemControlService items) {
        this(plugin, combat, logout, messages, audit, items, null);
    }

    public PnRelogCommand(PnRelogPlugin plugin, CombatService combat, LogoutService logout,
                           MessageService messages, AuditService audit, ItemControlService items,
                          UpdateService updates) {
        this.plugin = plugin;
        this.combat = combat;
        this.logout = logout;
        this.messages = messages;
        this.audit = audit;
        this.items = items;
        this.updates = updates;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("status")) return status(sender, args);
        if (!sender.hasPermission("pnrelog.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        return switch (subcommand) {
            case "stats" -> stats(sender);
            case "tag" -> tag(sender, args);
            case "clear" -> clear(sender, args);
            case "permit" -> permit(sender, args);
            case "history" -> history(sender, args);
            case "copy" -> copy(sender);
            case "update" -> update(sender, args);
            case "reload" -> reload(sender);
            default -> {
                messages.list("usage").forEach(sender::sendMessage);
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("pnrelog.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            if (!(sender instanceof Player player)) {
                messages.list("usage").forEach(sender::sendMessage);
                return true;
            }
            if (!sender.hasPermission("pnrelog.status")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = player;
        }
        if (target == null) {
            messages.send(sender, "player-not-found");
            return true;
        }
        Optional<CombatSnapshot> optional = combat.getCombat(target.getUniqueId());
        if (optional.isEmpty()) {
            messages.send(sender, "not-in-combat", Map.of("player", target.getName()));
            return true;
        }
        CombatSnapshot snapshot = optional.get();
        String opponents = snapshot.opponents().stream().map(OpponentSnapshot::playerName)
                .reduce((left, right) -> left + ", " + right).orElse("-");
        messages.send(sender, "status", Map.of(
                "player", target.getName(),
                "time", secondsCeil(snapshot.remainingMillis(combat.now())),
                "opponents", opponents,
                "damage_dealt", oneDecimal(snapshot.damageDealt()),
                "damage_taken", oneDecimal(snapshot.damageTaken())
        ));
        return true;
    }

    private boolean stats(CommandSender sender) {
        long circuitSeconds = secondsCeil(logout.circuitRemainingMillis());
        String circuit = logout.isCircuitOpen() ? "&cOPEN " + circuitSeconds + "s" : "&aCLOSED";
        messages.send(sender, "stats", Map.of(
                "links", combat.activeLinkCount(),
                "players", combat.getTaggedPlayers().size(),
                "pending", logout.pendingPenaltyCount(),
                "circuit", circuit
        ));
        return true;
    }

    private boolean tag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        Player first = Bukkit.getPlayerExact(args[1]);
        if (first == null) {
            messages.send(sender, "player-not-found");
            return true;
        }
        Player second = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        int seconds;
        if (second != null) seconds = args.length >= 4 ? parseInt(args[3], -1)
                : (int) (combat.settings().combat().durationMillis() / 1000L);
        else seconds = args.length >= 3 ? parseInt(args[2], -1)
                : (int) (combat.settings().combat().durationMillis() / 1000L);
        if (seconds < 1 || seconds > 3600) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        boolean tagged = second == null ? combat.tag(first, Duration.ofSeconds(seconds))
                : combat.tag(first, second, Duration.ofSeconds(seconds), TagCause.ADMIN);
        if (!tagged) {
            messages.send(sender, "reload-failed", Map.of("error", "бой отменён API или исключениями"));
            return true;
        }
        messages.send(sender, "tagged", Map.of("player", first.getName(),
                "opponent", second == null ? "-" : second.getName(), "time", seconds));
        return true;
    }

    private boolean clear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        if (args[1].equalsIgnoreCase("all")) {
            combat.clearAll(CombatEndReason.ADMIN);
            messages.send(sender, "cleared-all");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return true;
        }
        if (!combat.clear(target.getUniqueId(), CombatEndReason.ADMIN)) {
            messages.send(sender, "not-in-combat", Map.of("player", target.getName()));
            return true;
        }
        messages.send(sender, "cleared", Map.of("player", target.getName()));
        return true;
    }

    private boolean permit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return true;
        }
        int seconds = args.length >= 3 ? parseInt(args[2], -1) : 10;
        if (seconds < 1 || seconds > 300) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "admin";
        combat.grantLogoutPermit(target.getUniqueId(), Duration.ofSeconds(seconds), reason);
        messages.send(sender, "permit-granted", Map.of("player", target.getName(), "time", seconds));
        return true;
    }

    private boolean reload(CommandSender sender) {
        Optional<String> error = plugin.reloadRuntime();
        if (error.isPresent()) messages.send(sender, "reload-failed", Map.of("error", error.get()));
        else messages.send(sender, "reloaded");
        return true;
    }

    private boolean copy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "copy-empty");
            return true;
        }
        Optional<String> encoded = items.encodeMainHand(player);
        if (encoded.isEmpty()) messages.send(sender, "copy-empty");
        else messages.send(sender, "copy-value", Map.of("value", encoded.get()));
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.list("usage").forEach(sender::sendMessage);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            messages.send(sender, "player-not-found");
            return true;
        }
        int limit = args.length >= 3 ? parseInt(args[2], 5) : 5;
        limit = Math.max(1, Math.min(20, limit));
        List<AuditRecord> records = audit.history(target.getUniqueId(), limit);
        if (records.isEmpty()) {
            messages.send(sender, "history-empty", Map.of("player", target.getName()));
            return true;
        }
        for (AuditRecord record : records) {
            messages.send(sender, "history-line", Map.of(
                    "time", HISTORY_TIME.format(record.at()),
                    "type", record.type(), "detail", record.detail()));
        }
        return true;
    }

    private boolean update(CommandSender sender, String[] args) {
        if (updates == null) return true;
        if (args.length >= 2 && args[1].equalsIgnoreCase("install")) updates.install(sender);
        else updates.checkAndNotify(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "status"));
            if (sender.hasPermission("pnrelog.admin")) {
                options.addAll(List.of("stats", "tag", "clear", "permit", "history", "copy", "update", "reload"));
            }
            return matches(args[0], options);
        }
        if (args.length == 2 && List.of("status", "tag", "clear", "permit", "history")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            if (args[0].equalsIgnoreCase("clear")) names.add("all");
            return matches(args[1], names);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tag")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return matches(args[2], names);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("update")) return matches(args[1], List.of("check", "install"));
        return Collections.emptyList();
    }

    private static List<String> matches(String input, List<String> options) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long secondsCeil(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
