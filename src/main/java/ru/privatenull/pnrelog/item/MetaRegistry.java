package ru.privatenull.pnrelog.item;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.privatenull.pnrelog.api.item.MetaMatcher;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MetaRegistry {
    private final Map<String, MetaMatcher> matchers = new ConcurrentHashMap<>();

    public MetaRegistry() {
        registerDefaults();
    }

    public void register(String id, MetaMatcher matcher) {
        String normalized = normalize(id);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Meta matcher id cannot be empty");
        matchers.put(normalized, Objects.requireNonNull(matcher, "matcher"));
    }

    public MetaMatcher get(String id) {
        return matchers.get(normalize(id));
    }

    public void reserve(String id) {
        String normalized = normalize(id);
        if (!normalized.isEmpty()) matchers.putIfAbsent(normalized, (first, second) -> false);
    }

    public Set<String> ids() {
        return Set.copyOf(matchers.keySet());
    }

    public boolean matches(ItemStack configured, ItemStack actual, Set<String> matcherIds) {
        if (configured == null || actual == null) return false;
        for (String id : matcherIds) {
            MetaMatcher matcher = get(id);
            if (matcher == null || !matcher.matches(configured, actual)) return false;
        }
        return true;
    }

    private void registerDefaults() {
        register("SIMILAR", ItemStack::isSimilar);
        register("META", (first, second) -> Objects.equals(first.getItemMeta(), second.getItemMeta()));
        register("MATERIAL", (first, second) -> first.getType() == second.getType());
        register("ITEM_FLAGS", (first, second) -> flags(first).equals(flags(second)));
        register("DISPLAY_NAME", (first, second) -> Objects.equals(displayName(first), displayName(second)));
        register("LORE", (first, second) -> Objects.equals(lore(first), lore(second)));
        register("ENCHANTMENTS", (first, second) -> first.getEnchantments().equals(second.getEnchantments()));
        register("ATTRIBUTES", (first, second) -> Objects.equals(attributes(first), attributes(second)));
        register("PDC", (first, second) -> sameMetaProperty(first, second,
                meta -> meta.getPersistentDataContainer()));
        register("UNBREAKABLE", (first, second) -> sameMetaProperty(first, second, ItemMeta::isUnbreakable));
        register("POTION_EFFECTS", (first, second) -> first.getItemMeta() instanceof PotionMeta firstMeta
                && second.getItemMeta() instanceof PotionMeta secondMeta
                && firstMeta.getCustomEffects().equals(secondMeta.getCustomEffects()));
        register("POTION_BASE", MetaRegistry::samePotionBase);
        register("COLOR", (first, second) -> first.getItemMeta() instanceof LeatherArmorMeta firstMeta
                && second.getItemMeta() instanceof LeatherArmorMeta secondMeta
                && firstMeta.getColor().equals(secondMeta.getColor()));
        register("CUSTOM_MODEL_DATA", (first, second) -> {
            ItemMeta firstMeta = first.getItemMeta();
            ItemMeta secondMeta = second.getItemMeta();
            if (firstMeta == null || secondMeta == null) return firstMeta == secondMeta;
            return firstMeta.hasCustomModelData() == secondMeta.hasCustomModelData()
                    && (!firstMeta.hasCustomModelData()
                    || firstMeta.getCustomModelData() == secondMeta.getCustomModelData());
        });
        register("SKULL", (first, second) -> first.getItemMeta() instanceof SkullMeta firstMeta
                && second.getItemMeta() instanceof SkullMeta secondMeta
                && Objects.equals(firstMeta.getOwningPlayer(), secondMeta.getOwningPlayer()));
    }

    private static Set<ItemFlag> flags(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? Set.of() : meta.getItemFlags();
    }

    private static String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private static Object lore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getLore();
    }

    private static Object attributes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getAttributeModifiers();
    }

    private static boolean sameMetaProperty(ItemStack first, ItemStack second,
                                            java.util.function.Function<ItemMeta, Object> property) {
        ItemMeta firstMeta = first.getItemMeta();
        ItemMeta secondMeta = second.getItemMeta();
        if (firstMeta == null || secondMeta == null) return firstMeta == secondMeta;
        return Objects.equals(property.apply(firstMeta), property.apply(secondMeta));
    }

    @SuppressWarnings("deprecation")
    private static boolean samePotionBase(ItemStack first, ItemStack second) {
        if (!(first.getItemMeta() instanceof PotionMeta firstMeta)
                || !(second.getItemMeta() instanceof PotionMeta secondMeta)) return false;
        try {
            Object firstType = PotionMeta.class.getMethod("getBasePotionType").invoke(firstMeta);
            Object secondType = PotionMeta.class.getMethod("getBasePotionType").invoke(secondMeta);
            return Objects.equals(firstType, secondType);
        } catch (NoSuchMethodException ignored) {
            return Objects.equals(firstMeta.getBasePotionData(), secondMeta.getBasePotionData());
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private static String normalize(String id) {
        return id == null ? "" : id.strip().toUpperCase(Locale.ROOT);
    }
}
