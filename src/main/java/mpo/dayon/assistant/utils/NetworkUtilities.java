package mpo.dayon.assistant.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import mpo.dayon.common.log.Log;

public interface NetworkUtilities {
	static List<String> getInetAddresses() {
		final List<String> addresses = new ArrayList<>(8);

		try {
			InetAddress loopback = null;

			final Enumeration<NetworkInterface> nintfs = NetworkInterface.getNetworkInterfaces();
			while (nintfs.hasMoreElements()) {
				final Enumeration<InetAddress> inetAddresses = nintfs.nextElement().getInetAddresses();
				while (inetAddresses.hasMoreElements()) {
					final InetAddress inetAddress = inetAddresses.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						addresses.add(inetAddress.getHostAddress());
					} else {
						loopback = inetAddress;
					}
				}
			}

			if (loopback != null) {
				addresses.add(loopback.getHostAddress());
			}
		} catch (SocketException ex) {
			Log.warn("Inet Addresses error!", ex);
		}

		return addresses;
	}

}
