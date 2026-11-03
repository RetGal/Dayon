package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkClipboardFilesMessageTest {

    @TempDir
    Path tempDir;

    @Test
    void testUnmarshall() throws IOException, ClassNotFoundException {
        // given
        ObjectInputStream in = mock(ObjectInputStream.class);
        NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
        String tmpDir = System.getProperty("java.io.tmpdir");
        FileMetaData metaData = new FileMetaData("test.txt", 100, "basePath");
        List<FileMetaData> metaDataList = new ArrayList<>();
        metaDataList.add(metaData);
        when(in.readObject()).thenReturn(metaDataList);
        when(in.read(any(byte[].class), anyInt(), anyInt())).thenReturn(100);

        // when
        NetworkClipboardFilesHelper result = NetworkClipboardFilesMessage.unmarshall(in, helper, tmpDir);

        // then
        assertNotNull(result);
        assertEquals(1, result.getFileMetadatas().size());
        assertEquals("test.txt", result.getFileMetadatas().get(0).getFileName());
        assertEquals(100, result.getFileMetadatas().get(0).getFileSize());

    }

    @Test
    void marshallAndUnmarshallFilesAndDirectories() throws IOException {
        // given
        final Path source = Files.createDirectories(tempDir.resolve("source").resolve("nested"));
        Files.writeString(tempDir.resolve("source").resolve("a.txt"), "aaaa");
        Files.writeString(source.resolve("b.txt"), "bb");
        final Path target = Files.createDirectories(tempDir.resolve("target"));
        final long totalSize = 6;
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(Collections.singletonList(tempDir.resolve("source").toFile()), totalSize, tempDir.resolve("source").toString());

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }

        // when
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
            do {
                helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            } while (!helper.isDone());

            // then
            assertEquals(2, helper.getFiles().size());
            final File root = helper.getFiles().get(0).getParentFile();
            assertEquals("aaaa", Files.readString(root.toPath().resolve("a.txt")));
            assertEquals("bb", Files.readString(root.toPath().resolve("nested").resolve("b.txt")));
        }
    }

    @Test
    void getWireSizeContainsTheTypeAndTheRemainingSize() {
        // given
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(Collections.emptyList(), 42, tempDir.toString());

        // when, then
        assertEquals(43, message.getWireSize());
        assertEquals(NetworkMessageType.CLIPBOARD_FILES, message.getType());
        assertTrue(message.getFiles().isEmpty());
        assertEquals("Clipboard transfer", message.toString());
    }
}
