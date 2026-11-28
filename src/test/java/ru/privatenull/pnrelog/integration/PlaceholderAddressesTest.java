package ru.privatenull.pnrelog.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;

/** Адреса PlaceholderAPI {@code %pnrelog_player_<ник>_<ключ>%} и {@code %pnrelog_opponents_contains_<ник>%}. */
class PlaceholderAddressesTest {
    private PublishedAnswers answers;

    /** Значения без сервера: у ника «Bob» есть противник «Eve», у любого другого нет. */
    private static final class Fake extends PnRelogPlaceholders {
        Fake() {
            super(null);
        }

        @Override
        public String value(UUID id, String key) {
            return key.toUpperCase();
        }

        @Override
        public String yesNo(boolean yes) {
            return yes ? "Да" : "Нет";
        }

        @Override
        public boolean hasOpponent(UUID id, String name) {
            return name.equals("Eve");
        }

        @Override
        public String formattedTime(UUID id) {
            return "1м";
        }

        @Override
        public UUID idOf(String name) {
            return UUID.nameUUIDFromBytes(name.getBytes());
        }
    }

    @BeforeEach
    void register() {
        PlaceholderEngine engine = new PlaceholderEngine(problem -> {
            throw new AssertionError(problem);
        });
        PlaceholderAdapter adapter = new PlaceholderAdapter() {
            public String getId() {
                return "placeholderapi";
            }

            public boolean isAvailable() {
                return true;
            }

            public String render(UUID playerId, String text) {
                return text;
            }

            public AutoCloseable publish(String prefix, PublishedAnswers published) {
                answers = published;
                return () -> { };
            }
        };
        engine.register(adapter);
        PlaceholderService scope = engine.scope("pnrelog/pnrelog");
        PnRelogPlaceholders fake = new Fake();
        for (String key : PnRelogPlaceholders.KEYS) {
            scope.value(key.replace('_', '.'), "", "pnrelog", viewer -> fake.value(viewer.getPlayerId(), key));
        }
        scope.registerPublished("pnrelog", new PlayerValues(fake), new PlayerFormats(fake),
                new PlayerContainsFormat(fake), new OpponentsValues(fake), new OpponentsFormat(fake));
    }

    private String ask(String params) {
        return answers.answer(UUID.randomUUID(), params, 0);
    }

    @Test
    void ownKeysKeepTheirOldNames() {
        assertEquals("IN", ask("in"));
        assertEquals("IN_FORMATTED", ask("in_formatted"));
        assertEquals("TIME_FORMATTED", ask("time_formatted"));
        assertEquals("OPPONENT_COUNT", ask("opponent_count"));
        assertEquals("DAMAGE_DEALT", ask("damage_dealt"));
        assertEquals("LAST_ATTACKER", ask("last_attacker"));
    }

    @Test
    void anotherPlayersKeys() {
        assertEquals("ACTIVE", ask("player_Alex_active"));
        assertEquals("IN", ask("player_Alex_in"));
        assertEquals("IN_FORMATTED", ask("player_Alex_in_formatted"));
        assertEquals("TIME", ask("player_Alex_time"));
        assertEquals("1м", ask("player_Alex_time_formatted"));
        assertEquals("OPPONENTS", ask("player_Alex_opponents"));
        assertEquals("OPPONENT_COUNT", ask("player_Alex_opponent_count"));
        assertEquals("HITS_TAKEN", ask("player_Alex_hits_taken"));
        assertEquals("LAST_ATTACKER", ask("player_Alex_last_attacker"));
    }

    @Test
    void opponentsContainsOfAnotherPlayer() {
        assertEquals("true", ask("player_Alex_opponents_contains_Eve"));
        assertEquals("false", ask("player_Alex_opponents_contains_Zed"));
        assertEquals("Да", ask("player_Alex_opponents_contains_Eve_formatted"));
        assertEquals("Нет", ask("player_Alex_opponents_contains_Zed_formatted"));
    }

    @Test
    void opponentsContainsOfTheViewer() {
        assertEquals("OPPONENTS", ask("opponents"));
        assertEquals("true", ask("opponents_contains_Eve"));
        assertEquals("Да", ask("opponents_contains_Eve_formatted"));
    }

    @Test
    void nickWithUnderscoreNeedsTheEscape() {
        assertNull(ask("player_Mr_X_time"));
        assertEquals("TIME", ask("player_Mr~5FX_time"));
    }
}
