package ru.privatenull.pnrelog.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import ru.privatenull.pnrelog.PnRelogPlugin;
import ru.privatenull.pnrelog.api.item.InteractionHandler;
import ru.privatenull.pnrelog.api.item.ItemControlApi;
import ru.privatenull.pnrelog.api.item.ItemRole;
import ru.privatenull.pnrelog.api.item.ItemRuleView;
import ru.privatenull.pnrelog.api.item.ItemNameProvider;
import ru.privatenull.pnrelog.api.item.MetaMatcher;
import ru.privatenull.pnrelog.api.item.ObservedItem;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.text.MessageService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemControlService implements ItemControlApi, Listener {
    private record CooldownKey(UUID playerId, String ruleId) {
    }

    private final PnRelogPlugin plugin;
    private final CombatService combat;
    private final MessageService messages;
    private final PluginScheduler scheduler;
    private final MetaRegistry metaRegistry = new MetaRegistry();
    private final Map<Class<? extends Event>, List<InteractionHandler<?>>> interactions = new ConcurrentHashMap<>();
    private final Set<Class<? extends Event>> registeredEventClasses = ConcurrentHashMap.newKeySet();
    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile List<ItemRule> cooldownRules = List.of();
    private volatile List<ItemRule> preventionRules = List.of();
    private volatile Map<String, ItemRule> cooldownsById = Map.of();
    private volatile ItemNameProvider itemNameProvider = item -> prettify(item.getType());

    public ItemControlService(PnRelogPlugin plugin, CombatService combat,
                              MessageService messages, PluginScheduler scheduler) {
        this.plugin = plugin;
        this.combat = combat;
        this.messages = messages;
        this.scheduler = scheduler;
        registerDefaults();
    }

    public void load(org.bukkit.configuration.file.FileConfiguration config) {
        configureLangHelper(config);
        for (String id : config.getStringList("items.custom-matchers")) metaRegistry.reserve(id);
        ItemRuleLoader.Rules loaded = ItemRuleLoader.load(config, metaRegistry, itemNameProvider);
        Map<String, ItemRule> byId = new HashMap<>();
        for (ItemRule rule : loaded.cooldowns()) byId.put(rule.id(), rule);
        cooldownRules = loaded.cooldowns();
        preventionRules = loaded.preventions();
        cooldownsById = Map.copyOf(byId);
    }

    @Override
    public void registerMetaMatcher(String id, MetaMatcher matcher) {
        metaRegistry.register(id, matcher);
    }

    @Override
    public void setItemNameProvider(ItemNameProvider provider) {
        itemNameProvider = java.util.Objects.requireNonNull(provider, "provider");
    }

    @Override
    public <T extends Event> void registerInteraction(Class<T> eventClass, InteractionHandler<T> handler) {
        interactions.computeIfAbsent(eventClass, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(handler);
        if (registeredEventClasses.add(eventClass)) registerDynamic(eventClass);
    }

    @Override
    public Optional<ItemRuleView> findCooldownRule(ItemStack item) {
        return findRule(cooldownRules, item).map(ItemRule::view);
    }

    @Override
    public Optional<ItemRuleView> findPreventionRule(ItemStack item) {
        return findRule(preventionRules, item).map(ItemRule::view);
    }

    @Override
    public long remainingCooldownMillis(UUID playerId, String ruleId) {
        Long expiresAt = cooldowns.get(new CooldownKey(playerId, ruleId.toLowerCase(Locale.ROOT)));
        if (expiresAt == null) return 0L;
        long remaining = expiresAt - combat.now();
        if (remaining <= 0L) {
            cooldowns.remove(new CooldownKey(playerId, ruleId.toLowerCase(Locale.ROOT)), expiresAt);
            return 0L;
        }
        return remaining;
    }

    @Override
    public Set<String> registeredInteractions() {
        Set<String> output = new java.util.HashSet<>();
        for (List<InteractionHandler<?>> handlers : interactions.values()) {
            for (InteractionHandler<?> handler : handlers) output.add(handler.id());
        }
        return Set.copyOf(output);
    }

    @Override
    public Set<String> registeredMetaMatchers() {
        return metaRegistry.ids();
    }

    public void clearPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        cooldowns.keySet().removeIf(key -> key.playerId().equals(playerId));
        for (ItemRule rule : cooldownRules) {
            if (rule.materialCooldown()) player.setCooldown(rule.prototype().getType(), 0);
        }
    }

    public Optional<String> encodeMainHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return Optional.empty();
        return Optional.of(java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
    }

    @EventHandler
    public void onCombatEnd(ru.privatenull.pnrelog.api.event.CombatEndEvent event) {
        Player player = Bukkit.getPlayer(event.getCombat().playerId());
        if (player != null) clearPlayer(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType() == Material.AIR) return;
        findRule(cooldownRules, item).ifPresent(rule -> {
            long remaining = remainingCooldownMillis(event.getPlayer().getUniqueId(), rule.id());
            if (remaining > 0L) {
                messages.send(event.getPlayer(), "item-held-cooldown", Map.of(
                        "item", rule.displayName(), "time", secondsCeil(remaining)));
                Bukkit.getPluginManager().callEvent(new ru.privatenull.pnrelog.api.event.ItemControlEvent(
                        event.getPlayer(), ru.privatenull.pnrelog.api.event.ItemControlEvent.Type.HELD,
                        rule.id(), rule.displayName(), remaining));
            }
        });
    }

    private <T extends Event> void registerDynamic(Class<T> eventClass) {
        EventExecutor executor = (listener, event) -> {
            if (event.getClass() == eventClass) process(eventClass.cast(event));
        };
        Bukkit.getPluginManager().registerEvent(eventClass, new Listener() { }, EventPriority.HIGHEST,
                executor, plugin, true);
    }

    private <T extends Event> void process(T event) {
        List<InteractionHandler<?>> rawHandlers = interactions.get(event.getClass());
        if (rawHandlers == null) return;
        for (InteractionHandler<?> raw : rawHandlers) {
            @SuppressWarnings("unchecked") InteractionHandler<T> handler = (InteractionHandler<T>) raw;
            if (!handler.predicate().test(event)) continue;
            Player player = handler.playerExtractor().apply(event);
            if (player == null || !combat.isInCombat(player.getUniqueId())) continue;
            for (ObservedItem observed : handler.itemExtractor().apply(event)) {
                if (observed == null || observed.item().getType() == Material.AIR) continue;
                if (check(player, handler.id(), observed)) {
                    if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean check(Player player, String interaction, ObservedItem observed) {
        ItemRule cooldownRule = matching(cooldownRules, interaction, observed);
        if (cooldownRule != null && !player.hasPermission("pnrelog.bypass.cooldowns")) {
            long remaining = remainingCooldownMillis(player.getUniqueId(), cooldownRule.id());
            if (remaining > 0L) {
                messages.send(player, "item-cooldown", Map.of(
                        "item", cooldownRule.displayName(), "time", secondsCeil(remaining)));
                Bukkit.getPluginManager().callEvent(new ru.privatenull.pnrelog.api.event.ItemControlEvent(
                        player, ru.privatenull.pnrelog.api.event.ItemControlEvent.Type.COOLDOWN_BLOCKED,
                        cooldownRule.id(), cooldownRule.displayName(), remaining));
                return true;
            }
            applyCooldown(player, cooldownRule);
        }
        ItemRule preventionRule = matching(preventionRules, interaction, observed);
        if (preventionRule != null && !player.hasPermission("pnrelog.bypass.prevention")) {
            messages.send(player, "item-prevented", Map.of("item", preventionRule.displayName()));
            Bukkit.getPluginManager().callEvent(new ru.privatenull.pnrelog.api.event.ItemControlEvent(
                    player, ru.privatenull.pnrelog.api.event.ItemControlEvent.Type.PREVENTED,
                    preventionRule.id(), preventionRule.displayName(), 0L));
            if (preventionRule.materialCooldown()) {
                scheduler.runEntity(player, () -> player.setCooldown(preventionRule.prototype().getType(), 20));
            }
            return true;
        }
        return false;
    }

    private void applyCooldown(Player player, ItemRule rule) {
        applyCooldownRule(player, rule);
        for (String linkedId : rule.linkedRules()) {
            ItemRule linked = cooldownsById.get(linkedId);
            if (linked != null) applyCooldownRule(player, linked);
        }
    }

    private void applyCooldownRule(Player player, ItemRule rule) {
        long expiresAt = saturatingAdd(combat.now(), rule.cooldownMillis());
        cooldowns.put(new CooldownKey(player.getUniqueId(), rule.id()), expiresAt);
        if (rule.materialCooldown()) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, (rule.cooldownMillis() + 49L) / 50L);
            scheduler.runEntity(player, () -> player.setCooldown(rule.prototype().getType(), ticks));
        }
        scheduler.runEntityLater(player, () -> {
            CooldownKey key = new CooldownKey(player.getUniqueId(), rule.id());
            Long current = cooldowns.get(key);
            if (current != null && current == expiresAt && combat.now() >= expiresAt) {
                cooldowns.remove(key, current);
                if (player.isOnline()) {
                    messages.send(player, "item-cooldown-ended", Map.of("item", rule.displayName()));
                    Bukkit.getPluginManager().callEvent(new ru.privatenull.pnrelog.api.event.ItemControlEvent(
                            player, ru.privatenull.pnrelog.api.event.ItemControlEvent.Type.COOLDOWN_ENDED,
                            rule.id(), rule.displayName(), 0L));
                }
            }
        }, Math.max(1L, (rule.cooldownMillis() + 49L) / 50L + 1L));
    }

    private ItemRule matching(List<ItemRule> rules, String interaction, ObservedItem observed) {
        for (ItemRule rule : rules) {
            if (!rule.interactions().contains(interaction) || !rule.roles().contains(observed.role())) continue;
            if (metaRegistry.matches(rule.prototype(), observed.item(), rule.matchers())) return rule;
        }
        return null;
    }

    private Optional<ItemRule> findRule(List<ItemRule> rules, ItemStack item) {
        for (ItemRule rule : rules) {
            if (metaRegistry.matches(rule.prototype(), item, rule.matchers())) return Optional.of(rule);
        }
        return Optional.empty();
    }

    private void registerDefaults() {
        ItemInteractions.register(this);
    }

    private static long secondsCeil(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private void configureLangHelper(org.bukkit.configuration.file.FileConfiguration config) {
        if (!config.getBoolean("items.lang-helper.enabled", false)
                || !Bukkit.getPluginManager().isPluginEnabled("LangHelper")) return;
        String language = config.getString("items.lang-helper.language", "RU_RU");
        try {
            Class<?> helperType = Class.forName("ru.boomearo.langhelper.LangHelper");
            Object helper = helperType.getMethod("getInstance").invoke(null);
            Object manager = helperType.getMethod("getTranslateManager").invoke(helper);
            Class<?> languageType = Class.forName("ru.boomearo.langhelper.versions.LangType");
            @SuppressWarnings({"rawtypes", "unchecked"}) Object selected = Enum.valueOf(
                    (Class<? extends Enum>) languageType.asSubclass(Enum.class), language);
            java.lang.reflect.Method getName = manager.getClass().getMethod("getItemName", ItemStack.class, languageType);
            setItemNameProvider(item -> {
                try {
                    return String.valueOf(getName.invoke(manager, item, selected));
                } catch (ReflectiveOperationException exception) {
                    return prettify(item.getType());
                }
            });
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("LangHelper item translation недоступен: " + exception.getMessage());
        }
    }

    private static String prettify(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder output = new StringBuilder();
        for (String word : words) {
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }
}
