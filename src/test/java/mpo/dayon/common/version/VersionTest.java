package mpo.dayon.common.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.lang.Integer.parseInt;
import static mpo.dayon.common.version.Version.*;
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

    @ParameterizedTest
    @CsvSource({ "11.0.0,1.10.8", "12.0.0,1.10.8", "12.0.0,11.0.7", "11.0.7,12.0.0", "12.0.1,13.0.0" })
    void isCompatibleVersionShouldReturnTrueForHardCodedCompatibleVersions(String thatV, String otherV) {
        // given
        Version that = new Version(thatV);
        Version other = new Version(otherV);

        // when then
        assertTrue(isCompatibleVersion(other.getMajor(), other.getMinor(), that));
    }

    @ParameterizedTest
    @CsvSource({ "15,14", "15,13"})
    void isOutdatedVersionShouldReturnTrueForOutdatedVersions(String thatV, String otherV) {
        // given when then
        assertTrue(isOutdatedVersion(parseInt(thatV), parseInt(otherV)));
    }

    @ParameterizedTest
    @CsvSource({ "15,16", "15,15", "0,13", "15,0", "0,0"})
    void isOutdatedVersionShouldReturnFalseForNotOutdatedVersions(String thatV, String otherV) {
        // given when then
        assertFalse(isOutdatedVersion(parseInt(thatV), parseInt(otherV)));
    }

    @ParameterizedTest
    @CsvSource({ "15.0.0", "0.0.0" })
    void isColoredVersionShouldReturnTrueForHardCodedVersions(String thatV) {
        // given
        Version that = new Version(thatV);

        // when then
        assertTrue(isColoredVersion(that.getMajor()));
    }

}
