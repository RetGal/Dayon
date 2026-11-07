package mpo.dayon.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import static java.lang.String.format;
import static mpo.dayon.common.network.FileUtilities.getReadableFiles;
import static mpo.dayon.common.network.FileUtilities.separatorsToSystem;
import static mpo.dayon.common.network.FileUtilities.calculateTotalFileSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FileUtilitiesTest {

    @TempDir
    Path tempDir;

    @Test
    @DisabledOnOs(WINDOWS)
    void separatorsToSystemLinux() {
        // given
        String windowsPath = "..\\transfer\\foo.bar";
        // when
        final String linuxPath = separatorsToSystem(windowsPath);
        // then
        assertEquals("../transfer/foo.bar", linuxPath);
    }

    @Test
    @DisabledOnOs(LINUX)
    void separatorsToSystemWindows() {
        // given
        String linuxPath = "../transfer/foo.bar";
        // when
        final String windowsPath = separatorsToSystem(linuxPath);
        // then
        assertEquals("..\\transfer\\foo.bar", windowsPath);
    }

    @Test
    void calculateTotalFileSizeTest() throws IOException {
        // given
        File testDirectory = new File(format("%s%c%s", System.getProperty("java.io.tmpdir"), File.separatorChar, "testDirectory"));
        testDirectory.mkdir();
        writeIntoFile(testDirectory, "aTest.txt", "test");
        writeIntoFile(testDirectory, "bTest.txt", "txt");
        // when
        final long totalFileSize = calculateTotalFileSize(getReadableFiles(Collections.singletonList(testDirectory)));
        // then
        assertEquals(7, totalFileSize);
    }

    @Test
    void calculateTotalFileSizeOfRegularFiles() throws IOException {
        // given
        File file = File.createTempFile("dayon", ".txt");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("unicorn");
        }
        // when
        final long totalFileSize = calculateTotalFileSize(Collections.singletonList(file));
        // then
        assertEquals(7, totalFileSize);
        assertEquals(0, calculateTotalFileSize(Collections.emptyList()));
    }

    @Test
    void separatorsToSystemReturnsNullForNull() {
        // when, then
        assertNull(separatorsToSystem(null));
    }

    @Test
    void getReadableFilesSkipsUnreadableFilesInDirectories() throws IOException {
        // given
        final Path root = Files.createDirectories(tempDir.resolve("source"));
        final Path nested = Files.createDirectories(root.resolve("nested"));
        final File readable = Files.writeString(root.resolve("visible.txt"), "visible").toFile();
        final File nestedReadable = Files.writeString(nested.resolve("nested.txt"), "nested").toFile();
        final File unreadable = new UnreadableFile(Files.writeString(nested.resolve(".hidden"), "hidden"));
        final File nestedDirectory = new FilteredDirectory(nested, nestedReadable, unreadable);
        final File source = new FilteredDirectory(root, readable, nestedDirectory);

        // when
        final List<File> readableFiles = getReadableFiles(Collections.singletonList(source));

        // then
        assertEquals(2, readableFiles.size());
        assertTrue(readableFiles.contains(readable));
        assertTrue(readableFiles.contains(nestedReadable));
        assertFalse(readableFiles.contains(unreadable));
    }

    @Test
    void calculateTotalFileSizeIgnoresUnreadableFiles() throws IOException {
        // given
        final Path visible = Files.writeString(tempDir.resolve("visible.txt"), "visible");
        final File unreadable = new UnreadableFile(Files.writeString(tempDir.resolve(".hidden"), "hidden"));

        // when, then
        assertEquals(7, calculateTotalFileSize(getReadableFiles(Collections.singletonList(visible.toFile()))));
        assertEquals(0, calculateTotalFileSize(getReadableFiles(Collections.singletonList(unreadable))));
    }

    @Test
    void getReadableFilesSkipsFilesThatCannotBeOpened() {
        // given
        final File unopenable = new UnopenableFile(tempDir.resolve("missing.txt"));

        // when
        final List<File> readableFiles = getReadableFiles(Collections.singletonList(unopenable));

        // then
        assertTrue(unopenable.canRead());
        assertTrue(readableFiles.isEmpty());
    }

    private static final class UnreadableFile extends File {
        private UnreadableFile(Path path) {
            super(path.toString());
        }

        @Override
        public boolean canRead() {
            return false;
        }
    }

    private static final class UnopenableFile extends File {
        private final Path missing;

        private UnopenableFile(Path path) {
            super(path.toString());
            this.missing = path.resolveSibling(path.getFileName() + ".missing");
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public Path toPath() {
            return missing;
        }
    }

    private static final class FilteredDirectory extends File {
        private final File[] children;

        private FilteredDirectory(Path path, File... children) {
            super(path.toString());
            this.children = children.clone();
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public File[] listFiles() {
            return children.clone();
        }
    }

    private void writeIntoFile(File aDir, String fileName, String content) throws IOException {
        File file = new File(format("%s%c%s", aDir.getPath(), File.separatorChar, fileName));
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(content);
        writer.close();
    }
}
