package mpo.dayon.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SystemUtilitiesTest {
	
	@ParameterizedTest
	@CsvSource({ "10.0.", "2.5.6.256" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv4Address(String ipv4) {
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv4));
	}

	@ParameterizedTest
	@CsvSource({ "10.0.0.10", "2.5.6.255" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv4Address(String ipv4) {
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(ipv4));
	}

	@ParameterizedTest
	@CsvSource({ "abcd:1234:abcd:1234:abcd:1234:abcd:", "abcd:1234:abcd:1234:abcd:1234:abcd:snafu" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv6Address(String ipv6) {
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}

	@ParameterizedTest
	@CsvSource({ "ac:0:0:0:0:0:0:dc", "ac::dc" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv6Address(String ipv6) {
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}

	@ParameterizedTest
	@CsvSource({ "snafu.example.", "snafu..example.com" })
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidHostname(String hostName) {
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}

	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForTooLongHostname() {
		// given
		String hostName = "snaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaafu.com";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}

	@ParameterizedTest
	@CsvSource({ "snafu.example.com", "localhost" })
	void isValidIpAdressOrHostNameShouldReturnTrueForValidHostname(String hostName) {
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}

	@Test
	void checksumShouldReturnComputedChecksum() {
		// given
		String base33 = "1CHECK";
		// when, then
		assertEquals("E", SystemUtilities.checksum(base33));
	}

	@Test
	void isValidTokenShouldReturnFalseForEmptyToken() {
		// given
		String token = "";

		// when, then
		assertFalse(SystemUtilities.isValidToken(token));
	}

	@ParameterizedTest
	@CsvSource({ "SQL3", "COVID", "1CHECK2" })
	void isValidTokenShouldReturnFalseForInvalidToken(String token) {
		// when, then
		assertFalse(SystemUtilities.isValidToken(token));
	}

	@ParameterizedTest
	@CsvSource({ "TQP3", "1CHECKE" })
	void isValidTokenShouldReturnTrueForValidToken(String token) {
		// when, then
		assertTrue(SystemUtilities.isValidToken(token));
	}
}
