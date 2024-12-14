package mpo.dayon.common.utils;

import static mpo.dayon.common.utils.SystemUtilities.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;

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

}
