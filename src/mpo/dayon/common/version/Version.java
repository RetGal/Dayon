package mpo.dayon.common.version;

public class Version {
	private static final Version NULL = new Version(null);

	private final String version;

	private final int major;

	private final int minor;

	private Version(String version) {
		this.version = version == null ? "0.0 #0" : version;

		final int dotPos = this.version.indexOf('.');
		final int spacePos = this.version.indexOf(' ');

		final String smajor = this.version.substring(0, dotPos);
		final String sminor = this.version.substring(dotPos + 1, spacePos);

		this.major = Integer.valueOf(smajor);
		this.minor = Integer.valueOf(sminor);
	}

	public static Version get() {
		final String v = Version.class.getPackage().getImplementationVersion();

		if (v == null) {
			return NULL;
		}

		return new Version(v);
	}

	public boolean isNull() {
		return this == NULL;
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
