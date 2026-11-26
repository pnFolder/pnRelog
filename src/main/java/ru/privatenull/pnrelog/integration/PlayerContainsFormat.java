package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

import java.util.UUID;

/** Третья часть группы {@code player}: {@code opponents_contains_<ник>_formatted}. */
@Placeholders("player")
public final class PlayerContainsFormat {
    private final PnRelogPlaceholders values;

    public PlayerContainsFormat(PnRelogPlaceholders values) {
        this.values = values;
    }

    @Placeholder(bare = true)
    public Reply<View> player(@Argument ArgumentValue<String> name) {
        return Reply.next(new View(values, values.idOf(name.required())));
    }

    /** {@code opponents.contains.<ник>.formatted}. */
    public static final class View {
        private final PnRelogPlaceholders values;
        private final UUID id;

        View(PnRelogPlaceholders values, UUID id) {
            this.values = values;
            this.id = id;
        }

        @Placeholder
        public Reply<Contains> opponents() {
            return Reply.next(new Contains(values, id));
        }
    }

    public static final class Contains {
        private final PnRelogPlaceholders values;
        private final UUID id;

        Contains(PnRelogPlaceholders values, UUID id) {
            this.values = values;
            this.id = id;
        }

        @Placeholder
        public Reply<Formatted> contains(@Argument ArgumentValue<String> name) {
            return Reply.next(new Formatted(values.yesNo(values.hasOpponent(id, name.required()))));
        }
    }
}
