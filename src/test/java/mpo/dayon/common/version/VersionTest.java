package mpo.dayon.common.version;

import org.junit.jupiter.api.Test;

import static mpo.dayon.common.version.Version.isCompatibleVersion;
import static mpo.dayon.common.version.Version.isProd;
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

    @Test
    void shouldParseVersion() {
        // given
        String versionString = "2.42.4";

        // when
        Version version = new Version(versionString);

        // then
        assertEquals(2, version.getMajor());
        assertEquals(42, version.getMinor());
    }

    @Test
    void isProdShouldReturnFalseForDevVersion() {
        // given when then
        assertFalse(isProd(0,0));
    }

    @Test
    void isProdShouldReturnTrueForProdVersion() {
        // given
        Version prodVersion = new Version("1.10.8");

        // when then
        assertTrue(isProd(prodVersion.getMajor(),prodVersion.getMinor()));
    }

    @Test
    void isCompatibleVersionShouldReturnTrueIfOtherVersionIsNotProd() {
        // given
        Version that = new Version("1.10.8");
        Version devVersion = new Version(null);

        // when then
        assertTrue(isCompatibleVersion(devVersion.getMajor(), devVersion.getMinor(), that));
    }

    @Test
    void isCompatibleVersionShouldReturnTrueIfThisVersionIsNotProd() {
        // given
        Version that = new Version(null);
        Version prodVersion = new Version("1.10.8");

        // when then
        assertTrue(isCompatibleVersion(prodVersion.getMajor(), prodVersion.getMinor(), that));
    }

    @Test
    void isCompatibleVersionShouldReturnTrueIfBothMajorMinorVersionsMatch() {
        // given
        Version that = new Version("1.10.8");
        Version other = new Version("1.10.2");

        // when then
        assertTrue(isCompatibleVersion(other.getMajor(), other.getMinor(), that));
    }

    @Test
    void isCompatibleVersionShouldReturnFalseIfNotBothMajorMinorVersionsMatch() {
        // given
        Version that = new Version("1.10.8");
        Version other = new Version("1.9.8");

        // when then
        assertFalse(isCompatibleVersion(other.getMajor(), other.getMinor(), that));
    }

    @Test
    void isCompatibleVersionShouldReturnTrueForHardCodedCompatibleVerions() {
        // given
        Version that = new Version("11.0.0");
        Version other = new Version("1.10.8");

        // when then
        assertTrue(isCompatibleVersion(other.getMajor(), other.getMinor(), that));
    }

}
