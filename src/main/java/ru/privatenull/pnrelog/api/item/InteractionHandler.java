package ru.privatenull.pnrelog.api.item;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public record InteractionHandler<T extends Event>(
        String id,
        Predicate<T> predicate,
        Function<T, Player> playerExtractor,
        Function<T, List<ObservedItem>> itemExtractor
) {
    public InteractionHandler {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be empty");
        id = id.strip().toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(playerExtractor, "playerExtractor");
        Objects.requireNonNull(itemExtractor, "itemExtractor");
    }
}
