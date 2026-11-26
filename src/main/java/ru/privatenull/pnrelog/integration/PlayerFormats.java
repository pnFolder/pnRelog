package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

import java.util.UUID;

/** Вторая часть группы {@code player}: {@code in_formatted}, {@code time_formatted} и {@code opponents_contains_<ник>}. */
@Placeholders("player")
public final class PlayerFormats {
    private final PnRelogPlaceholders values;

    public PlayerFormats(PnRelogPlaceholders values) {
        this.values = values;
    }

    @Placeholder(bare = true)
    public Reply<View> player(@Argument ArgumentValue<String> name) {
        return Reply.next(new View(values, values.idOf(name.required())));
    }

    /** {@code in.formatted}, {@code time.formatted}, {@code opponents.contains.<ник>}. */
    public static final class View {
        private final PnRelogPlaceholders values;
        private final UUID id;

        View(PnRelogPlaceholders values, UUID id) {
            this.values = values;
            this.id = id;
        }

        @Placeholder
        public Reply<Formatted> in() {
            return Reply.next(new Formatted(values.value(id, "in_formatted")));
        }

        @Placeholder
        public Reply<Formatted> time() {
            return Reply.next(new Formatted(values.formattedTime(id)));
        }

        @Placeholder
        public Reply<Contains> opponents() {
            return Reply.next(new Contains(values, id));
        }
    }

    /** {@code contains.<ник>}: есть ли ник среди противников, {@code true} или {@code false}. */
    public static final class Contains {
        private final PnRelogPlaceholders values;
        private final UUID id;

        Contains(PnRelogPlaceholders values, UUID id) {
            this.values = values;
            this.id = id;
        }

        @Placeholder("Есть ли ник среди противников")
        public Reply<String> contains(@Argument ArgumentValue<String> name) {
            return Reply.value(Boolean.toString(values.hasOpponent(id, name.required())));
        }
    }
}
