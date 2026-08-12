package ru.privatenull.pnrelog.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {
    @Test
    void parsesReleaseAndJarAsset() {
        String json = "{\"html_url\":\"https://github.com/test/pnRelog/releases/tag/v1.2.0\","
                + "\"tag_name\":\"v1.2.0\",\"assets\":[{\"browser_download_url\":"
                + "\"https://github.com/test/pnRelog/releases/download/v1.2.0/pnRelog-1.2.0.jar\"}]}";
        UpdateService.Release release = UpdateService.parse(json);

        assertEquals("1.2.0", release.version());
        assertTrue(release.asset().toString().endsWith(".jar"));
    }

    @Test
    void comparesNumericVersions() {
        assertTrue(UpdateService.compareVersions("1.10.0", "1.9.9") > 0);
        assertEquals(0, UpdateService.compareVersions("v1.0", "1.0.0"));
        assertTrue(UpdateService.compareVersions("1.0.1", "1.0.2") < 0);
    }
}
