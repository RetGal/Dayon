package mpo.dayon.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;

import static java.lang.String.format;
import static mpo.dayon.common.utils.FileUtilities.separatorsToSystem;
import static mpo.dayon.common.utils.FileUtilities.calculateTotalFileSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FileUtilitiesTest {

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
        final long totalFileSize = calculateTotalFileSize(Collections.singletonList(testDirectory));
        // then
        assertEquals(7, totalFileSize);
    }

    private void writeIntoFile(File aDir, String fileName, String content) throws IOException {
        File file = new File(format("%s%c%s", aDir.getPath(), File.separatorChar, fileName));
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(content);
        writer.close();
    }
}
