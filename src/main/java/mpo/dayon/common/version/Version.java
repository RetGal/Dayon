package mpo.dayon.common.version;

import mpo.dayon.common.log.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class Version {
    private static final Version VERSION_NULL = new Version(null);
    public static final String RELEASE_LOCATION = "https://github.com/retgal/Dayon/releases/";

    private final String numericVersion;
    private String latestVersion;

    private final int major;
    private final int minor;

    Version(String numericVersion) {
        this.numericVersion = numericVersion == null ? "0.0.0" : numericVersion;

        final int firstDotPos = this.numericVersion.indexOf('.');
        final int lastDotPos = this.numericVersion.lastIndexOf('.');

        this.major = Integer.parseInt(this.numericVersion.substring(0, firstDotPos));
        this.minor = Integer.parseInt(this.numericVersion.substring(firstDotPos + 1, lastDotPos));
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
        return 'v' + numericVersion;
    }

    boolean isLatestVersion() {
        return numericVersion.equals(getLatestRelease());
    }

    public String getLatestRelease() {
        if (latestVersion == null) {
            // HttpClient doesn't implement AutoCloseable nor close before Java 21!
            @java.lang.SuppressWarnings("squid:S2095")
            HttpClient client = HttpClient.newHttpClient();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASE_LOCATION + "latest"))
                        .timeout(Duration.ofSeconds(5))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpHeaders responseHeaders = client.send(request, HttpResponse.BodyHandlers.discarding()).headers();
                String latestLocation = responseHeaders.firstValue("Location").orElse(null);
                if (latestLocation != null) {
                    latestVersion = latestLocation.substring(latestLocation.lastIndexOf('v'));
                } else {
                    Log.warn("Failed to read latest version");
                }
            } catch (IOException | InterruptedException e) {
                Log.error("Exception", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return latestVersion;
    }

    public static boolean isCompatibleVersion(int major, int minor, Version that) {
        if (!isProd(major, minor) || !isProd(that.major, that.major)) {
            return true;
        }
        if (that.major > 10 && (major > 10 || (major == 1 && minor == 10))) {
            return true;
        }
        return that.major == major && that.minor == minor;
    }

    public static boolean isOutdatedVersion(int major, int otherMajor) {
        if (major == 0 || otherMajor == 0) {
            return false;
        }
        return major > otherMajor;
    }

    public static boolean isColoredVersion(int major) {
        return major > 14 || major == 0;
    }

    static boolean isProd(int major, int minor) {
        return major + minor > 0;
    }
}
