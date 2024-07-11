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
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELEASE_LOCATION + "latest"))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String latestLocation = response.headers().firstValue("Location").orElse(null);
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
        if (!isProd(major, minor) || !isProd(that.getMajor(), that.getMajor())) {
            return true;
        }
        if (that.getMajor() > 10 && (major > 10 || (major == 1 && minor == 10))) {
            return true;
        }
        return that.getMajor() == major && that.getMinor() == minor;
    }

    static boolean isProd(int major, int minor) {
        return major + minor > 0;
    }
}
