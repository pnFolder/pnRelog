package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

import java.util.UUID;

/**
 * {@code %pnrelog_player_<ник>_<ключ>%}: бой другого игрока, ключи те же, что у своих. Один адрес собран из трёх
 * классов группы {@code player} ({@link PlayerValues}, {@link PlayerFormats}, {@link PlayerContainsFormat}):
 * у значения и у его вида {@code _formatted} один и тот же путь, а в одном классе имя занять дважды нельзя.
 */
@Placeholders("player")
public final class PlayerValues {
    private final PnRelogPlaceholders values;

    public PlayerValues(PnRelogPlaceholders values) {
        this.values = values;
    }

    @Placeholder(value = "player.<ник>.<ключ>: бой другого игрока", bare = true)
    public Reply<View> player(@Argument ArgumentValue<String> name) {
        return Reply.next(new View(values, values.idOf(name.required())));
    }

    /** Простые ключи боя игрока. */
    public static final class View {
        private final PnRelogPlaceholders values;
        private final UUID id;

        View(PnRelogPlaceholders values, UUID id) {
            this.values = values;
            this.id = id;
        }

        @Placeholder
        public Reply<String> active() {
            return Reply.value(values.value(id, "active"));
        }

        @Placeholder
        public Reply<String> in() {
            return Reply.value(values.value(id, "in"));
        }

        @Placeholder
        public Reply<String> time() {
            return Reply.value(values.value(id, "time"));
        }

        @Placeholder
        public Reply<String> opponents() {
            return Reply.value(values.value(id, "opponents"));
        }

        @Placeholder
        public Reply<Count> opponent() {
            return Reply.next(new Count(values.value(id, "opponent_count")));
        }

        @Placeholder
        public Reply<Pair> damage() {
            return Reply.next(new Pair(values.value(id, "damage_dealt"), values.value(id, "damage_taken")));
        }

        @Placeholder
        public Reply<Pair> hits() {
            return Reply.next(new Pair(values.value(id, "hits_dealt"), values.value(id, "hits_taken")));
        }

        @Placeholder
        public Reply<Last> last() {
            return Reply.next(new Last(values.value(id, "last_attacker")));
        }
    }

    /** {@code opponent.count}. */
    public static final class Count {
        private final String count;

        Count(String count) {
            this.count = count;
        }

        @Placeholder
        public Reply<String> count() {
            return Reply.value(count);
        }
    }

    /** {@code damage.dealt/taken} и {@code hits.dealt/taken}. */
    public static final class Pair {
        private final String dealt;
        private final String taken;

        Pair(String dealt, String taken) {
            this.dealt = dealt;
            this.taken = taken;
        }

        @Placeholder
        public Reply<String> dealt() {
            return Reply.value(dealt);
        }

        @Placeholder
        public Reply<String> taken() {
            return Reply.value(taken);
        }
    }

    /** {@code last.attacker}. */
    public static final class Last {
        private final String attacker;

        Last(String attacker) {
            this.attacker = attacker;
        }

        @Placeholder
        public Reply<String> attacker() {
            return Reply.value(attacker);
        }
    }
}
