package ru.privatenull.pnrelog.action;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pnrelog.api.action.ActionApi;
import ru.privatenull.pnrelog.api.action.ActionTrigger;
import ru.privatenull.pnrelog.api.event.CombatContinueEvent;
import ru.privatenull.pnrelog.api.event.CombatEndEvent;
import ru.privatenull.pnrelog.api.event.CombatJoinEvent;
import ru.privatenull.pnrelog.api.event.CombatMergeEvent;
import ru.privatenull.pnrelog.api.event.CombatStartEvent;
import ru.privatenull.pnrelog.api.event.CombatTickEvent;
import ru.privatenull.pnrelog.api.event.ItemControlEvent;
import ru.privatenull.pnrelog.api.event.PlayerKickInCombatEvent;
import ru.privatenull.pnrelog.api.event.PlayerLeaveInCombatEvent;
import ru.privatenull.pnrelog.api.event.RestrictionDeniedEvent;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.text.Colorizer;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionService implements ActionApi, Listener {
    private static final Pattern ACTION = Pattern.compile("^\\[([A-Z_]+)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private final PluginScheduler scheduler;
    private final ru.privatenull.pnrelog.combat.CombatService combat;
    private final java.util.logging.Logger logger;
    private final Map<UUID, Map<Material, List<ItemStack>>> removedItems = new ConcurrentHashMap<>();
    private volatile Map<ActionTrigger, List<String>> actions = Map.of();

    public ActionService(PluginScheduler scheduler, java.util.logging.Logger logger,
                         ru.privatenull.pnrelog.combat.CombatService combat) {
        this.scheduler = scheduler;
        this.logger = logger;
        this.combat = combat;
    }

    public void load(FileConfiguration config) {
        EnumMap<ActionTrigger, List<String>> loaded = new EnumMap<>(ActionTrigger.class);
        ConfigurationSection section = config.getConfigurationSection("actions");
        for (ActionTrigger trigger : ActionTrigger.values()) {
            List<String> entries = section == null ? List.of()
                    : section.getStringList(trigger.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            List<String> validated = new ArrayList<>();
            for (String entry : entries) {
                Matcher matcher = ACTION.matcher(entry.strip());
                if (!matcher.matches()) {
                    logger.warning("Пропущен action без [TYPE]: " + entry);
                    continue;
                }
                try {
                    Type.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
                    validated.add(entry.strip());
                } catch (IllegalArgumentException exception) {
                    logger.warning("Неизвестный action type: " + matcher.group(1));
                }
            }
            loaded.put(trigger, List.copyOf(validated));
        }
        actions = Map.copyOf(loaded);
    }

    @Override
    public void execute(ActionTrigger trigger, Player player, String... arguments) {
        if (player == null) return;
        for (String entry : actions.getOrDefault(trigger, List.of())) executeOne(player, entry, arguments);
    }

    @Override
    public List<String> configuredActions(ActionTrigger trigger) {
        return actions.getOrDefault(trigger, List.of());
    }

    private void executeOne(Player player, String entry, String... arguments) {
        Matcher matcher = ACTION.matcher(entry);
        if (!matcher.matches()) return;
        Type type = Type.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        String context = placeholders(player, matcher.group(2), arguments);
        switch (type) {
            case MESSAGE -> player.sendMessage(Colorizer.color(context));
            case ACTIONBAR -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(Colorizer.color(context)));
            case SOUND -> sound(player, context);
            case TITLE -> title(player, context);
            case PLAYER -> playerAction(player, context);
            case CONSOLE -> scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), slashless(context)));
            case BROADCAST_MESSAGE -> broadcast(p -> p.sendMessage(Colorizer.color(context)));
            case BROADCAST_ACTIONBAR -> broadcast(p -> p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(Colorizer.color(context))));
            case BROADCAST_SOUND -> broadcast(p -> sound(p, context));
            case BROADCAST_TITLE -> broadcast(p -> title(p, context));
            case REMOVE_ITEMS -> removeItems(player, context);
            case BACK_ITEMS -> backItems(player);
        }
    }

    private void broadcast(java.util.function.Consumer<Player> action) {
        for (Player target : Bukkit.getOnlinePlayers()) scheduler.runEntity(target, () -> action.accept(target));
    }

    private void sound(Player player, String context) {
        String[] parts = context.split(";", -1);
        try {
            Sound sound = Sound.valueOf(parts[0].strip().toUpperCase(Locale.ROOT));
            float volume = parts.length >= 2 ? Float.parseFloat(parts[1]) : 1F;
            float pitch = parts.length >= 3 ? Float.parseFloat(parts[2]) : 1F;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            logger.warning("Некорректный SOUND action: " + context);
        }
    }

    private void title(Player player, String context) {
        String[] parts = context.split(";", -1);
        String title = parts.length >= 1 ? Colorizer.color(parts[0]) : "";
        String subtitle = parts.length >= 2 ? Colorizer.color(parts[1]) : "";
        try {
            int fadeIn = parts.length >= 3 ? Integer.parseInt(parts[2]) : 10;
            int stay = parts.length >= 4 ? Integer.parseInt(parts[3]) : 40;
            int fadeOut = parts.length >= 5 ? Integer.parseInt(parts[4]) : 10;
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (NumberFormatException exception) {
            logger.warning("Некорректный TITLE action: " + context);
        }
    }

    private static void playerAction(Player player, String context) {
        if (context.startsWith("/")) player.performCommand(context.substring(1));
        else player.chat(context);
    }

    private void removeItems(Player player, String context) {
        Material material = Material.matchMaterial(context.strip().toUpperCase(Locale.ROOT));
        if (material == null) {
            logger.warning("Некорректный REMOVE_ITEMS action: " + context);
            return;
        }
        List<ItemStack> removed = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && stack.getType() == material) {
                removed.add(stack.clone());
                player.getInventory().setItem(slot, null);
            }
        }
        removedItems.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(material, List.copyOf(removed));
    }

    private void backItems(Player player) {
        Map<Material, List<ItemStack>> stored = removedItems.remove(player.getUniqueId());
        if (stored == null) return;
        for (List<ItemStack> stacks : stored.values()) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                    stacks.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
            for (ItemStack item : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private String placeholders(Player player, String input, String... arguments) {
        String output = input;
        int sequential = 0;
        for (int index = 0; index < arguments.length; index++) output = output.replace("{" + index + "}", arguments[index]);
        while (output.contains("{}") && sequential < arguments.length) {
            output = output.replaceFirst("\\{\\}", Matcher.quoteReplacement(arguments[sequential++]));
        }
        return ru.privatenull.pnrelog.text.PlaceholderSupport.parse(player, output);
    }

    @EventHandler public void onStart(CombatStartEvent event) {
        execute(ActionTrigger.COMBAT_START_ATTACKER, event.getAttacker(), event.getTarget().getName());
        execute(ActionTrigger.COMBAT_START_TARGET, event.getTarget(), event.getAttacker().getName());
    }
    @EventHandler public void onJoin(CombatJoinEvent event) {
        execute(ActionTrigger.COMBAT_JOIN, event.getJoiningPlayer(),
                event.isAttackerJoining() ? event.getTarget().getName() : event.getAttacker().getName());
    }
    @EventHandler public void onMerge(CombatMergeEvent event) {
        execute(ActionTrigger.COMBAT_MERGE, event.getAttacker(), event.getTarget().getName());
        execute(ActionTrigger.COMBAT_MERGE, event.getTarget(), event.getAttacker().getName());
    }
    @EventHandler public void onContinue(CombatContinueEvent event) {
        execute(ActionTrigger.COMBAT_CONTINUE, event.getAttacker(), event.getTarget().getName());
    }
    @EventHandler public void onTick(CombatTickEvent event) {
        Player player = Bukkit.getPlayer(event.getCombat().playerId());
        if (player != null) execute(ActionTrigger.COMBAT_TICK, player,
                Long.toString((event.getCombat().remainingMillis(combat.now()) + 999L) / 1000L));
    }
    @EventHandler public void onEnd(CombatEndEvent event) {
        Player player = Bukkit.getPlayer(event.getCombat().playerId());
        if (player != null) execute(ActionTrigger.COMBAT_END, player, event.getReason().name());
    }
    @EventHandler public void onLeave(PlayerLeaveInCombatEvent event) {
        Player player = Bukkit.getPlayer(event.getCombat().playerId());
        if (player != null) execute(ActionTrigger.COMBAT_LOGOUT, player, player.getName());
    }
    @EventHandler public void onKick(PlayerKickInCombatEvent event) {
        Player player = Bukkit.getPlayer(event.getCombat().playerId());
        if (player != null) execute(ActionTrigger.COMBAT_KICK, player, player.getName(), event.getReason());
    }
    @EventHandler public void onRestriction(RestrictionDeniedEvent event) {
        ActionTrigger trigger = switch (event.getType()) {
            case COMMAND -> ActionTrigger.COMMAND_BLOCKED;
            case PLAYER_COMMAND -> ActionTrigger.PLAYER_COMMAND_BLOCKED;
            case TELEPORT -> ActionTrigger.TELEPORT_BLOCKED;
            case ELYTRA -> ActionTrigger.ELYTRA_BLOCKED;
        };
        execute(trigger, event.getPlayer(), event.getValue(), Long.toString((event.getRemainingMillis() + 999L) / 1000L));
    }
    @EventHandler public void onItem(ItemControlEvent event) {
        ActionTrigger trigger = switch (event.getType()) {
            case COOLDOWN_BLOCKED -> ActionTrigger.ITEM_COOLDOWN;
            case PREVENTED -> ActionTrigger.ITEM_PREVENTED;
            case COOLDOWN_ENDED -> ActionTrigger.ITEM_COOLDOWN_ENDED;
            case HELD -> ActionTrigger.ITEM_HELD;
        };
        execute(trigger, event.getPlayer(), event.getItemName(),
                Long.toString((event.getRemainingMillis() + 999L) / 1000L), event.getRuleId());
    }

    private static String slashless(String command) { return command.startsWith("/") ? command.substring(1) : command; }

    private enum Type {
        MESSAGE, ACTIONBAR, SOUND, TITLE, PLAYER, CONSOLE,
        BROADCAST_MESSAGE, BROADCAST_ACTIONBAR, BROADCAST_SOUND, BROADCAST_TITLE,
        REMOVE_ITEMS, BACK_ITEMS
    }
}
