package mpo.dayon.common.log.file;

import mpo.dayon.common.log.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileAppenderTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsMessagesAndStackTraces() throws IOException {
        // given
        final File file = tempDir.resolve("dayon.log").toFile();
        final FileAppender appender = new FileAppender(file.getAbsolutePath());

        // when
        appender.append(LogLevel.INFO, "hello", null);
        appender.append(LogLevel.ERROR, "boom", new IllegalStateException("kaputt"));

        // then
        final String content = Files.readString(file.toPath());
        assertTrue(content.contains(" INFO"));
        assertTrue(content.contains("hello"));
        assertTrue(content.contains("ERROR"));
        assertTrue(content.contains("boom"));
        assertTrue(content.contains("java.lang.IllegalStateException: kaputt"));
    }

    @Test
    void appendsWithoutAMessage() throws IOException {
        // given
        final File file = tempDir.resolve("dayon.log").toFile();
        final FileAppender appender = new FileAppender(file.getAbsolutePath());

        // when
        appender.append(LogLevel.DEBUG, null, null);

        // then
        final String content = Files.readString(file.toPath());
        assertTrue(content.contains("DEBUG"));
    }

    @Test
    void appendsToAnExistingFile() throws IOException {
        // given
        final File file = tempDir.resolve("dayon.log").toFile();
        Files.writeString(file.toPath(), "existing");

        // when
        final FileAppender appender = new FileAppender(file.getAbsolutePath());
        appender.append(LogLevel.WARN, "appended", null);

        // then
        final String content = Files.readString(file.toPath());
        assertTrue(content.startsWith("existing"));
        assertTrue(content.contains("appended"));
    }

    @Test
    void rollsOverWhenTheMaximumFileSizeIsReached() throws IOException {
        // given
        final File file = tempDir.resolve("dayon.log").toFile();
        final File surplus = new File(file.getAbsolutePath() + ".3");
        assertTrue(surplus.createNewFile());
        final FileAppender appender = new FileAppender(file.getAbsolutePath());
        final String chunk = "x".repeat(70 * 1024);

        // when
        for (int idx = 0; idx < 20; idx++) {
            appender.append(LogLevel.INFO, chunk, null);
        }

        // then
        final File backup = new File(file.getAbsolutePath() + ".1");
        assertTrue(backup.exists(), "the full log file should have been rotated");
        assertFalse(surplus.exists(), "the oldest backup should have been deleted");
        assertTrue(file.exists());
        assertTrue(file.length() < 1024 * 1024L, "the new log file should start over");
    }

    @Test
    void throwsForAnUnwritableLocation() {
        // given
        final String filename = tempDir.resolve("missing").resolve("dayon.log").toString();

        // when, then
        assertThrows(FileNotFoundException.class, () -> new FileAppender(filename));
    }
}
