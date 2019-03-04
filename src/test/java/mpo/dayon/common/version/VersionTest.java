package mpo.dayon.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @Test
    void shouldObtainLatestRelease() {
        // given
        Version version = Version.get();

        // when
        String latestRelease = version.getLatestRelease();

        // then
        assertTrue(version.isNull());
        assertFalse(version.isLatestVersion());
        assertTrue(latestRelease.startsWith("v"));
    }

}
