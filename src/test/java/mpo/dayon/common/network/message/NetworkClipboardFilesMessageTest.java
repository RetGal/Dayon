package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
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
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
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
    void marshallAndUnmarshallLargeFileInChunks() throws IOException {
        // given
        final byte[] content = new byte[2 * 1024 * 1024 + 123];
        new Random(42).nextBytes(content);
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        final Path file = Files.write(source.resolve("large.bin"), content);
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(file.toFile()), content.length, source.toString());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));

        // when
        NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            do {
                helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            } while (!helper.isDone());
        }

        // then
        assertArrayEquals(content, Files.readAllBytes(helper.getFiles().get(0).toPath()));
        assertNull(helper.getFileOutputStream(), "the file stream must be closed when the transfer is done");
    }

    @Test
    void unmarshallThrowsEOFExceptionWhenTheStreamEndsPrematurely() throws IOException {
        // given
        final List<FileMetaData> metadata = new ArrayList<>(Collections.singletonList(
                new FileMetaData("incomplete.bin", 10, "")));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            NetworkMessage.marshallEnum(out, NetworkMessageType.CLIPBOARD_FILES);
            out.writeObject(metadata);
            out.write(new byte[]{1, 2, 3, 4});
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));

        // when, then
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            final NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
            final NetworkClipboardFilesHelper incomplete = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            try {
                assertFalse(incomplete.isDone());
                assertThrows(EOFException.class,
                        () -> NetworkClipboardFilesMessage.unmarshall(in, incomplete, target.toString()));
            } finally {
                incomplete.close();
            }
        }
    }

    @Test
    void marshallAndUnmarshallDotFileBeforeAnotherSmallFile() throws IOException {
        // given
        final Path dotFile = tempDir.resolve(".hidden");
        final Path regularFile = tempDir.resolve("visible.txt");
        Files.writeString(dotFile, "hidden file contents");
        Files.writeString(regularFile, "visible file contents");
        final List<File> files = new ArrayList<>();
        files.add(dotFile.toFile());
        files.add(regularFile.toFile());
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(files, 41, tempDir.toString());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));

        // when
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
            do {
                helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            } while (!helper.isDone());

            // then
            final Path receivedRoot = helper.getFiles().get(0).getParentFile().toPath();
            assertEquals("hidden file contents", Files.readString(receivedRoot.resolve(".hidden")));
            assertEquals("visible file contents", Files.readString(receivedRoot.resolve("visible.txt")));
        }
    }

    @Test
    void unmarshallRejectsParentDirectorySegments() throws IOException {
        // given
        final List<FileMetaData> metadata = new ArrayList<>(Collections.singletonList(
                new FileMetaData(".." + File.separator + "escape.txt", 1, "")));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(metadata);
            out.writeByte(1);
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));
        final NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();

        // when, then
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            final IOException error = assertThrows(IOException.class,
                    () -> NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString()));
            assertEquals("Path escapes transfer directory", error.getMessage());
        }
        assertFalse(Files.exists(target.resolve("escape.txt")), "the escaping file must not be written");
        assertFalse(Files.exists(tempDir.resolve("escape.txt")), "the escaping file must not be written");
    }

    @Test
    void unmarshallRejectsDisallowedMetadataClasses() throws IOException {
        // given
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new HashMap<>());
        }
        final NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();

        // when, then
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertThrows(InvalidClassException.class,
                    () -> NetworkClipboardFilesMessage.unmarshall(in, helper, tempDir.toString()));
        }
    }

    @Test
    void skipsUnreadableFilesBeforeSendingMetadata() throws IOException, ClassNotFoundException {
        // given
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        final Path readable = Files.writeString(source.resolve("visible.txt"), "visible");
        final File unreadable = new UnreadableFile(Files.writeString(source.resolve(".hidden"), "hidden"));
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                List.of(unreadable, readable.toFile()), 7, source.toString());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // when
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }

        // then
        assertFalse(message.isEmpty());
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            @SuppressWarnings("unchecked")
            final List<FileMetaData> metadata = (List<FileMetaData>) in.readObject();
            assertEquals(1, metadata.size());
            assertTrue(metadata.get(0).getFileName().endsWith("visible.txt"));
            assertEquals(7, metadata.get(0).getFileSize());
            assertEquals("visible", new String(in.readNBytes(7), UTF_8));
        }
    }

    @Test
    void isEmptyWhenAllFilesAreUnreadable() throws IOException {
        // given
        final File unreadable = new UnreadableFile(Files.writeString(tempDir.resolve(".hidden"), "hidden"));

        // when
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(unreadable), 0, tempDir.toString());

        // then
        assertTrue(message.isEmpty());
    }

    @Test
    void marshallAndUnmarshallEmptyFilesAlongsideRegularOnes() throws IOException {
        // given
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        Files.createFile(source.resolve("empty.bin"));
        Files.writeString(source.resolve("content.txt"), "abc");
        Files.createFile(source.resolve("z-empty.bin"));
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(source.toFile()), 3, source.toString());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));

        // when
        NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            do {
                helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            } while (!helper.isDone());
        }

        // then
        final Path root = helper.getFiles().get(0).getParentFile().toPath();
        assertEquals(0, Files.size(root.resolve("empty.bin")));
        assertEquals(0, Files.size(root.resolve("z-empty.bin")));
        assertEquals("abc", Files.readString(root.resolve("content.txt")));
    }

    @Test
    void sendsExactlyTheSizeCapturedInTheMetadataWhenTheFileGrows() throws IOException, ClassNotFoundException {
        // given
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        final Path file = Files.writeString(source.resolve("growing.txt"), "12345");
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(file.toFile()), 5, source.toString());
        // the file grows after the metadata has been captured
        Files.writeString(file, "123456789");

        // when
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }

        // then
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            in.readObject();
            assertEquals("12345", new String(in.readAllBytes(), UTF_8));
        }
    }

    @Test
    void marshallFailsWhenAFileShrinksDuringTheTransfer() throws IOException {
        // given
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        final Path file = Files.writeString(source.resolve("shrinking.txt"), "12345");
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(file.toFile()), 5, source.toString());
        // the file shrinks after the metadata has been captured
        Files.writeString(file, "12");

        // when, then
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            final EOFException error = assertThrows(EOFException.class, () -> message.marshall(out));
            assertTrue(error.getMessage().contains("shrank"));
        }
    }

    @Test
    void reusesTheReadBufferForEveryChunk() throws IOException {
        // given
        final byte[] content = new byte[3 * 1024 * 1024];
        new Random(7).nextBytes(content);
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        final Path file = Files.write(source.resolve("large.bin"), content);
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.singletonList(file.toFile()), content.length, source.toString());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            message.marshall(out);
        }
        final Path target = Files.createDirectories(tempDir.resolve("target"));

        // when
        NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NetworkClipboardFilesMessage.installFileObjectInputFilter(in);
            assertEquals(NetworkMessageType.CLIPBOARD_FILES, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
            helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
            final byte[] buffer = helper.getReadBuffer();
            while (!helper.isDone()) {
                helper = NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());
                assertSame(buffer, helper.getReadBuffer(), "the buffer must not be reallocated per chunk");
            }
        }

        // then
        assertArrayEquals(content, Files.readAllBytes(helper.getFiles().get(0).toPath()));
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

    @Test
    void unmarshallAcceptsTransfersLargerThanTwoGigabytes() throws IOException, ClassNotFoundException {
        // given
        final ObjectInputStream in = mock(ObjectInputStream.class);
        final List<FileMetaData> metadata = new ArrayList<>();
        metadata.add(new FileMetaData("first.bin", 1L << 31, ""));
        metadata.add(new FileMetaData("second.bin", 1L << 31, ""));
        when(in.readObject()).thenReturn(metadata);
        // no payload is consumed: only the metadata validation is under test
        when(in.read(any(byte[].class), anyInt(), anyInt())).thenReturn(0);
        final Path target = Files.createDirectories(tempDir.resolve("target"));
        final NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();

        // when
        try {
            NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString());

            // then
            assertEquals(1L << 32, helper.getTotalFileBytesLeft());
            assertEquals(1L << 31, helper.getFileBytesLeft());
        } finally {
            helper.close();
        }
    }

    @Test
    void unmarshallRejectsNegativeFileSizes() throws IOException, ClassNotFoundException {
        // given
        final ObjectInputStream in = mock(ObjectInputStream.class);
        final List<FileMetaData> metadata = new ArrayList<>();
        metadata.add(new FileMetaData("negative.bin", -1, ""));
        metadata.add(new FileMetaData("positive.bin", 10, ""));
        when(in.readObject()).thenReturn(metadata);
        final Path target = Files.createDirectories(tempDir.resolve("target"));
        final NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();

        // when, then
        final IOException error = assertThrows(IOException.class,
                () -> NetworkClipboardFilesMessage.unmarshall(in, helper, target.toString()));
        assertEquals("Received invalid file size -1", error.getMessage());
        helper.close();
    }

    @Test
    void getWireSizeSaturatesAboveTwoGigabytes() {
        // given - the wire size is only used for local statistics
        final NetworkClipboardFilesMessage message = new NetworkClipboardFilesMessage(
                Collections.emptyList(), 3L << 30, tempDir.toString());

        // when, then
        assertEquals(Integer.MAX_VALUE, message.getWireSize());
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
