package mpo.dayon.smoke;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Verifies that every class in a jar can be loaded by the oldest JVM Dayon supports.
 *
 * <p>The Maven compiler release only constrains Dayon's own sources. Transitive
 * dependencies may be compiled for a newer JVM and end up in the shaded jar unnoticed;
 * the jar then fails at startup with {@code UnsupportedClassVersionError} on the
 * advertised minimum Java version. This check inspects the class file headers of the
 * final artifact, so it catches such dependencies regardless of how they got there.</p>
 *
 * <p>It is deliberately dependency free and runs through the JDK's single-file source
 * launcher (Java 11), so it works in the Maven and the Ant build alike. Multi-release
 * entries below {@code META-INF/versions/} are ignored: older JVMs never load them.</p>
 *
 * <p>Usage: {@code java BytecodeLevelCheck.java <jar> [<maximum-major-version>]}</p>
 */
public final class BytecodeLevelCheck {

    /** Class file major version of Java 11 (55 = 11  44). */
    private static final int DEFAULT_MAX_MAJOR_VERSION = 55;

    private BytecodeLevelCheck() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: BytecodeLevelCheck <jar> [<maximum-major-version>]");
        }

        Path jar = Paths.get(args[0]).toAbsolutePath().normalize();
        int maxMajor = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_MAX_MAJOR_VERSION;

        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("bytecode check failed: jar not found: " + jar);
        }

        SortedMap<Integer, List<String>> tooNew = new TreeMap<>();
        int scanned = 0;

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/versions/")) {
                    continue;
                }

                int major = readMajorVersion(zip.getInputStream(entry));
                if (major < 0) {
                    continue;
                }

                ++scanned;
                if (major > maxMajor) {
                    tooNew.computeIfAbsent(major, key -> new ArrayList<>()).add(name);
                }
            }
        }

        if (tooNew.isEmpty()) {
            System.out.println("bytecode check OK: "   + scanned + " classes in " + jar.getFileName() +
                     " target Java " + javaVersion(maxMajor) + " or lower");
            return;
        }

        throw new IllegalStateException(formatFailure(jar, maxMajor, scanned, tooNew));
    }

    private static int readMajorVersion(InputStream in) throws IOException {
        try (DataInputStream data = new DataInputStream(in)) {
            if (data.readInt() != 0xCAFEBABE) {
                return -1;
            }
            data.readUnsignedShort(); // minor version
            return data.readUnsignedShort();
        }
    }

    private static String formatFailure(Path jar, int maxMajor, int scanned,
                                        SortedMap<Integer, List<String>> tooNew) {
        StringBuilder message = new StringBuilder();
        message.append("bytecode check FAILED: ").append(jar.getFileName())
                .append(" must stay compatible with Java ").append(javaVersion(maxMajor))
                .append(" (class file version ").append(maxMajor).append("), but contains ")
                .append(tooNew.values().stream().mapToInt(List::size).sum())
                .append(" of ").append(scanned).append(" classes that require a newer JVM:\n");

        for (Map.Entry<Integer, List<String>> entry : tooNew.entrySet()) {
            int major = entry.getKey();
            List<String> classes = entry.getValue();
            message.append("  Java ").append(javaVersion(major))
                    .append(" (class file version ").append(major).append("): ")
                    .append(classes.size()).append(" classes\n");

            SortedMap<String, Integer> packages = new TreeMap<>();
            for (String className : classes) {
                packages.merge(packageRoot(className), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> packageEntry : packages.entrySet()) {
                message.append("    ").append(packageEntry.getKey()).append(": ")
                        .append(packageEntry.getValue()).append(" classes\n");
            }

            int sample = Math.min(5, classes.size());
            message.append("    e.g. ").append(String.join(", ", classes.subList(0, sample)));
            if (sample < classes.size()) {
                message.append(", ...");
            }
            message.append('\n');
        }

        message.append("A transitive dependency is compiled for a newer JVM than the advertised minimum.\n")
                .append("Pin the dependency that ships the packages above to a Java ").append(javaVersion(maxMajor))
                .append("-compatible version, or raise maven.compiler.release together with the minimum JRE\n")
                .append("declared in the packaging profiles (see the 'deb' profile's default-jre dependency).");
        return message.toString();
    }

    /** Reduces {@code org/ice4j/ice/Agent.class} to {@code org/ice4j} for readable grouping. */
    private static String packageRoot(String className) {
        int first = className.indexOf('/');
        if (first < 0) {
            return "<default package>";
        }
        int second = className.indexOf('/', first + 1);
        return second < 0 ? className.substring(0, first) : className.substring(0, second);
    }

    private static int javaVersion(int classFileMajor) {
        return classFileMajor - 44;
    }
}