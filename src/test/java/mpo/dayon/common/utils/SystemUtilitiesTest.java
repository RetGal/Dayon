package mpo.dayon.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SystemUtilitiesTest {
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnIncompleteIpv4Address() {
		// given
		String ipv4 = "10.0.";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv4));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv4Address() {
		// given
		String ipv4 = "2.5.6.256";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv4));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv4Address() {
		// given
		String ipv4 = "145.74.11.8";
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(ipv4));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnIncompleteIpv6Address() {
		// given
		String ipv6 = "abcd:1234:abcd:1234:abcd:1234:abcd:";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv6Address() {
		// given
		String ipv6 = "abcd:1234:abcd:1234:abcd:1234:abcd:snafu";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv6Address() {
		// given
		String ipv6 = "ac:0:0:0:0:0:0:dc";
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnTrueForCompressedValidIpv6Address() {
		// given
		String ipv6 = "ac::dc";
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(ipv6));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidHostname() {
		// given
		String hostName = "snafu.example.";
		// when, then
		assertFalse(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnFalseForAnotherInvalidHostname() {
		// given
		String hostName = "snafu..example.com";
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
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnTrueForValidHostname() {
		// given
		String hostName = "snafu.example.com";
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}
	
	@Test
	void isValidIpAdressOrHostNameShouldReturnTrueForAnotherValidHostname() {
		// given
		String hostName = "localhost";
		// when, then
		assertTrue(SystemUtilities.isValidIpAddressOrHostName(hostName));
	}

}
