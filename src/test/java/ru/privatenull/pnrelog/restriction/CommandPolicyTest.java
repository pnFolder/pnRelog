package ru.privatenull.pnrelog.restriction;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnrelog.config.PluginSettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandPolicyTest {
    @Test
    void blacklistMatchesSubcommandsAndNamespaces() {
        PluginSettings.Commands settings = new PluginSettings.Commands(true,
                PluginSettings.CommandMode.BLACKLIST, List.of("home", "warp secret"), true, List.of());

        assertTrue(CommandPolicy.blocked("/home base", settings));
        assertTrue(CommandPolicy.blocked("/essentials:home base", settings));
        assertTrue(CommandPolicy.blocked("/warp secret now", settings));
        assertFalse(CommandPolicy.blocked("/warp public", settings));
    }

    @Test
    void whitelistDoesNotTrustForeignNamespaces() {
        PluginSettings.Commands settings = new PluginSettings.Commands(true,
                PluginSettings.CommandMode.WHITELIST, List.of("msg"), true, List.of());

        assertFalse(CommandPolicy.blocked("/msg Steve hi", settings));
        assertTrue(CommandPolicy.blocked("/evil:msg Steve hi", settings));
        assertTrue(CommandPolicy.blocked("/spawn", settings));
    }
}
