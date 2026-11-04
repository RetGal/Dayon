package mpo.dayon.common.utils;

import static mpo.dayon.common.utils.SystemUtilities.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class SystemUtilitiesTest {
	
	@ParameterizedTest
	@CsvSource({ "77", "10.0.", "2.5.6.256" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv4Address(String ipv4) {
		// when, then
		assertFalse(isValidIpAddressOrHostName(ipv4));
	}

	@ParameterizedTest
	@CsvSource({ "10.0.0.10", "2.5.6.255" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv4Address(String ipv4) {
		// when, then
		assertTrue(isValidIpAddressOrHostName(ipv4));
	}

	@ParameterizedTest
	@CsvSource({ "abcd:1234:abcd:1234:abcd:1234:abcd:", "abcd:1234:abcd:1234:abcd:1234:abcd:snafu" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv6Address(String ipv6) {
		// when, then
		assertFalse(isValidIpAddressOrHostName(ipv6));
	}

	@ParameterizedTest
	@CsvSource({ "ac:0:0:0:0:0:0:dc", "ac::dc" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv6Address(String ipv6) {
		// when, then
		assertTrue(isValidIpAddressOrHostName(ipv6));
	}

	@ParameterizedTest
	@CsvSource({ "snafu.example.", "snafu..example.com" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidHostname(String hostName) {
		// when, then
		assertFalse(isValidIpAddressOrHostName(hostName));
	}

	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForTooLongHostname() {
		// given
		String hostName = "snaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaafu.com";
		// when, then
		assertFalse(isValidIpAddressOrHostName(hostName));
	}

	@ParameterizedTest
	@CsvSource({ "snafu.example.com", "localhost" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidHostname(String hostName) {
		// when, then
		assertTrue(isValidIpAddressOrHostName(hostName));
	}

	@ParameterizedTest
	@CsvSource({ "msH05Tnämé", "5540ýçT", "Im-Wìth-5TüpéD" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidMicrosoftHostname(String hostName) {
		// when, then
		assertTrue(isValidIpAddressOrHostName(hostName));
	}

	@Test
	void checksumShouldReturnComputedChecksum() throws NoSuchAlgorithmException {
		// given
		String base33 = "1CHECK";
		// when, then
		assertEquals("E", checksum(base33));
	}

	@Test
	void isValidTokenShouldReturnFalseForEmptyToken() throws NoSuchAlgorithmException {
		// given
		String token = "";

		// when, then
		assertFalse(isValidToken(token));
	}

	@ParameterizedTest
	@CsvSource({ "SQL3", "COVID", "1CHECK2" })
	void isValidTokenShouldReturnFalseForInvalidToken(String token) throws NoSuchAlgorithmException {
		// when, then
		assertFalse(isValidToken(token));
	}

	@ParameterizedTest
	@CsvSource({ "TQP3", "1CHECKE" })
	void isValidTokenShouldReturnTrueForValidToken(String token) throws NoSuchAlgorithmException {
		// when, then
		assertTrue(isValidToken(token));
	}

	@ParameterizedTest
	@CsvSource({"-1", "0", "66666", "X"})
	void isValidPortNumberShouldReturnFalseForInvalidPorts(String port) {
		// when, then
		assertFalse(isValidPortNumber(port));
	}

	@ParameterizedTest
	@CsvSource({"1", "80", "1234", "65535"})
	void isValidPortNumberShouldReturnTrueForValidPorts(String port) {
		// when, then
		assertTrue(isValidPortNumber(port));
	}

	@ParameterizedTest
	@CsvSource({"http://127.0.0.1", "http://localhost/token", "https://example.com/rvs/", "https://dayon.example.org", "https://example.org "})
	void isValidUrlStringShouldReturnTrueForValidUrls(String url) {
		// when, then
		assertTrue(isValidUrl(url));
	}

	@ParameterizedTest
	@CsvSource({"http://127.0.0.", "htt://localhost/token", "https://example.com!/rvs", "file://dayon.example", "sun.fun", "https://\uD83E\uDD84"})
	void isValidUrlStringShouldReturnFalseForInvalidUrls(String url) {
		// when, then
		assertFalse(isValidUrl(url));
	}

	@Test
	void shouldObtainWritableTempDir() throws IOException {
		// when, then
		assertTrue(Files.isWritable(new File(getTempDir()).toPath()), "TempDir should be writable");
	}

	@Test
	void shouldObtainJarDir() {
		// when, then
		assertTrue(Files.isDirectory(new File(getJarDir()).toPath()), "JarDir should be a directory");
	}

	@Test
	void shouldObtainQuickStartUri() {
		// when, then
		assertEquals("https://retgal.github.io/Dayon/assistant.html#assistant-setup", getQuickStartURI("assistant.html", "assistant").toString());
	}

	@Test
	void shouldObtainRamInfo() {
		// when, then
		assertTrue(getRamInfo().contains(" of "));
	}

	@Test
	void shouldObtainTheSystemProperties() {
		// when
		List<String> properties = getSystemProperties();

		// then
		assertFalse(properties.isEmpty());
		assertTrue(properties.stream().anyMatch(prop -> prop.contains("java.version")));
		assertTrue(properties.stream().anyMatch(prop -> prop.contains("line.separator") && prop.contains("\\")));
		assertEquals(String.join(System.lineSeparator(), properties), getSystemPropertiesEx());
	}

	@Test
	void shouldObtainTheDefaultLookAndFeel() {
		// when, then
		assertNotNull(getDefaultLookAndFeel());
	}

	@Test
	void safeCloseClosesAllCloseablesAndIgnoresErrors() {
		// given
		AtomicBoolean closed = new AtomicBoolean(false);
		Closeable closeable = () -> closed.set(true);
		Closeable failing = () -> {
			throw new IOException("snafu");
		};

		// when
		safeClose(closeable, null, failing);

		// then
		assertTrue(closed.get());
	}

	@Test
	void safeInterruptInterruptsTheThread() {
		// given
		Thread thread = new Thread(() -> {
		});
		thread.start();

		// when
		Thread result = safeInterrupt(thread);

		// then
		assertNull(result);
		assertTrue(thread.isInterrupted());
		assertNull(safeInterrupt(null));
	}

	@Test
	void isValidAddressEdgeCases() {
		// when, then
		assertTrue(isValidIpAddressOrHostName("192.168.0.1"));
		assertFalse(isValidIpAddressOrHostName("192.168.0.1.1.1"));
		assertFalse(isValidIpAddressOrHostName(""));
		assertFalse(isValidIpAddressOrHostName(null));
	}

	@Test
	void isNotSnappedWithARegularClassPath() {
		// given
		String classPath = System.getProperty(JAVA_CLASS_PATH);

		try {
			System.setProperty(JAVA_CLASS_PATH, "/opt/dayon/jar/dayon.jar");

			// when, then
			assertFalse(isSnapped());
			assertEquals("", getBuildNumber());
		} finally {
			System.setProperty(JAVA_CLASS_PATH, classPath);
		}
	}

	@Test
	void isSnappedAndComputesTheBuildNumber() {
		// given
		String classPath = System.getProperty(JAVA_CLASS_PATH);

		try {
			System.setProperty(JAVA_CLASS_PATH, "/snap/dayon/18.0.4/jar/dayon.jar");

			// when, then
			assertTrue(isSnapped());
			assertEquals("18.0.4", getBuildNumber());
			assertEquals("/snap/dayon/18.0.4/bin/dayon.browser", getSnapBrowserCommand());
		} finally {
			System.setProperty(JAVA_CLASS_PATH, classPath);
		}
	}

	@Test
	void isFlatWithAFlatpakVendor() {
		// given
		String vendor = System.getProperty("java.vendor");

		try {
			// when, then
			System.setProperty("java.vendor", "Flatpak");
			assertTrue(isFlat());
			System.setProperty("java.vendor", "Oracle Corporation");
			assertFalse(isFlat());
		} finally {
			System.setProperty("java.vendor", vendor);
		}
	}

	@Test
	void getTempDirCleansTheSnapTransferDir(@TempDir Path tempDir) throws IOException {
		// given
		String classPath = System.getProperty(JAVA_CLASS_PATH);
		String dayonHome = System.getProperty("dayon.home");
		Path transferDir = tempDir.resolve(".transfer");
		Path stale = Files.createDirectories(transferDir).resolve("stale.txt");
		Files.writeString(stale, "stale");
		System.setProperty(JAVA_CLASS_PATH, "/snap/dayon/18.0.4/jar/dayon.jar");
		System.setProperty("dayon.home", tempDir.toString());

		try {
			// when
			String temp = getTempDir();

			// then
			assertEquals(transferDir.toString(), temp);
			assertTrue(Files.isDirectory(transferDir), "the transfer dir must still exist");
			assertFalse(Files.exists(stale), "previous transfer files must be removed");
		} finally {
			System.setProperty(JAVA_CLASS_PATH, classPath);
			if (dayonHome == null) {
				System.clearProperty("dayon.home");
			} else {
				System.setProperty("dayon.home", dayonHome);
			}
		}
	}

}
