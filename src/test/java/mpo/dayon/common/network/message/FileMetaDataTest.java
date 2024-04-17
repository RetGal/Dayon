package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

class FileMetaDataTest {

    private static final String basePath = "tmp/";
    private final String fileName = format("%sfoo/bar.txt", basePath);
    private static final long fileSize = 10;

    @Test
    void getFileName() {
        // given
        FileMetaData fileMetaData = new FileMetaData(fileName, fileSize, basePath);

        // when then
        assertEquals("foo/bar.txt", fileMetaData.getFileName());
        assertEquals(fileSize, fileMetaData.getFileSize());
    }

    @Test
    void equals() {
        // given
        FileMetaData fileMetaData = new FileMetaData(fileName, fileSize, basePath);

        // when then
        assertEquals(fileMetaData, new FileMetaData(fileName, fileSize, basePath));
    }
}
