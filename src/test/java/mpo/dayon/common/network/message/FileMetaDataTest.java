package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

class FileMetaDataTest {

    private static final String BASE_PATH = "tmp/";
    private static final long FILE_SIZE = 10;
    private final String fileName = format("%sfoo/bar.txt", BASE_PATH);

    @Test
    void getFileName() {
        // given
        FileMetaData fileMetaData = new FileMetaData(fileName, FILE_SIZE, BASE_PATH);

        // when then
        assertEquals("foo/bar.txt", fileMetaData.getFileName());
        assertEquals(FILE_SIZE, fileMetaData.getFileSize());
    }

    @Test
    void equals() {
        // given
        FileMetaData fileMetaData = new FileMetaData(fileName, FILE_SIZE, BASE_PATH);

        // when then
        assertEquals(fileMetaData, new FileMetaData(fileName, FILE_SIZE, BASE_PATH));
    }
}
