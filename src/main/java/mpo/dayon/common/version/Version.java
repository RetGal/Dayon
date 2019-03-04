package mpo.dayon.common.version;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class Version {
	private static final Version VERSION_NULL = new Version(null);
	public static final String RELEASE_LOCATION = "https://github.com/retgal/dayon/releases/";

	private final String version;
	private String latestVersion;

	private final int major;
	private final int minor;

	private Version(String version) {
		this.version = version == null ? "0.0.0" : version;

		final int firstDotPos = this.version.indexOf('.');
		final int lastDotPos = this.version.lastIndexOf('.');

		this.major = Integer.valueOf(this.version.substring(0, firstDotPos));
		this.minor = Integer.valueOf(this.version.substring(firstDotPos + 1, lastDotPos));
	}

	public static Version get() {
		final String v = Version.class.getPackage().getImplementationVersion();

		if (v == null) {
			return VERSION_NULL;
		}

		return new Version(v);
	}

	public boolean isNull() {
		return this == VERSION_NULL;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	@Override
	public String toString() {
		return 'v' + version;
	}

	boolean isLatestVersion() {
		return getLatestRelease().equals(version);
	}

	public String getLatestRelease() {
		if (latestVersion == null) {
			HttpURLConnection conn = null;
			try {
				URL obj = new URL(RELEASE_LOCATION + "latest");
				conn = (HttpURLConnection) obj.openConnection();
				conn.setInstanceFollowRedirects(false);
			} catch (IOException e) {
				// offline?
			} finally {
				Objects.requireNonNull(conn).disconnect();
			}

			String latestLocation = conn.getHeaderField("Location");
			if (latestLocation != null) {
				latestVersion = latestLocation.substring(latestLocation.lastIndexOf('v'));
			}
		}
		return latestVersion;
	}
}
