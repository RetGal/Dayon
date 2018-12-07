package mpo.dayon.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class SystemUtilitiesTest {
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnIncompleteIpv4Address() {
		// given
		String ipv4 = "10.0.";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(ipv4));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv4Address() {
		// given
		String ipv4 = "2.5.6.256";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(ipv4));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnTrueForValidIpv4Address() {
		// given
		String ipv4 = "145.74.11.8";
		// when, then
		assertTrue(SystemUtilities.isValidIpAdressOrHostName(ipv4));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnIncompleteIpv6Address() {
		// given
		String ipv6 = "fe10:0:0:0";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(ipv6));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidIpv6Address() {
		// given
		String ipv6 = "fe10:0:0:0:dafc:11ff:fe4d:snafu";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(ipv6));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnTrueFoValidIpv6Address() {
		// given
		String ipv6 = "fe10:0:0:0:dafc:11ff:fe4d:a16f";
		// when, then
		assertTrue(SystemUtilities.isValidIpAdressOrHostName(ipv6));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnInvalidHostname() {
		// given
		String hostName = "snafu.example.";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(hostName));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnFalseForAnotherInvalidHostname() {
		// given
		String hostName = "snafu..example.com";
		// when, then
		assertFalse(SystemUtilities.isValidIpAdressOrHostName(hostName));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnTrueForValidHostname() {
		// given
		String hostName = "snafu.example.com";
		// when, then
		assertTrue(SystemUtilities.isValidIpAdressOrHostName(hostName));
	}
	
	@Test
	public void isValidIpAdressOrHostNameShouldReturnTrueForAnotherValidHostname() {
		// given
		String hostName = "localhost";
		// when, then
		assertTrue(SystemUtilities.isValidIpAdressOrHostName(hostName));
	}

}
