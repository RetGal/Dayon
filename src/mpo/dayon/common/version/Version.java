package mpo.dayon.common.version;

public class Version {
	private static final Version VERSION_NULL = new Version(null);

	private final String version;

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
		return "v" + version;
	}

}
