package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.FileUtilities;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.Math.*;
import static java.lang.String.format;
import static java.nio.file.attribute.PosixFilePermission.*;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    private final List<File> readableFiles;
    private final List<FileMetaData> fileMetaDatas;
    private final Long remainingTotalFilesSize;
    // Matches MAX_SEND_BUFFER_CAPACITY so that a sender chunk can be read in one go.
    private static final int MAX_READ_BUFFER_CAPACITY = 1048576;
    private static final int MAX_SEND_BUFFER_CAPACITY = 1048576;

    private static final ObjectInputFilter FILE_METADATA_FILTER = info -> {
        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        if (serialClass == ArrayList.class || serialClass == FileMetaData.class || serialClass == Object[].class) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    };

    public static void installFileObjectInputFilter(ObjectInputStream in) {
        in.setObjectInputFilter(FILE_METADATA_FILTER);
    }

    public NetworkClipboardFilesMessage(List<File> files, long remainingTotalFilesSize, String basePath) {
        this.files = Collections.unmodifiableList(files);
        this.readableFiles = Collections.unmodifiableList(FileUtilities.getReadableFiles(files));
        this.fileMetaDatas = getMetaData(readableFiles, basePath);
        this.remainingTotalFilesSize = remainingTotalFilesSize;
    }

    public static NetworkClipboardFilesHelper unmarshall(ObjectInputStream in, NetworkClipboardFilesHelper helper, String tmpDir) throws IOException {
        try {
            if (helper.getTransferId() == null) {
                initHelper(in, helper);
            }
            int position = helper.getPosition();
            FileMetaData meta = helper.getFileMetadatas().get(position);
            long fileSize = meta.getFileSize();
            Log.debug("%s", () -> format("FileSize: %d left: %d", fileSize, helper.getFileBytesLeft()));

            byte[] buffer = new byte[max(0, min(toIntExact(helper.getFileBytesLeft()), MAX_READ_BUFFER_CAPACITY))];
            int read = in.read(buffer, 0, buffer.length);
            if (read < 0) {
                throw new EOFException("Unexpected end of clipboard file transfer");
            }
            String fileName = FileUtilities.separatorsToSystem(meta.getFileName());
            if (helper.getFileOutputStream() == null) {
                Log.info("Receiving " + fileName);
            }
            writeToTempFile(helper, buffer, read, fileName, tmpDir);

            if (getRemainingTotalFilesSize(helper, read, position) == 0) {
                File[] filesArray = getRootPath(helper, tmpDir).toFile().listFiles();
                if (filesArray != null) {
                    helper.setFiles(Arrays.asList(filesArray));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not read clipboard file metadata", e);
        }
        return helper;
    }

    @SuppressWarnings("squid:S5135") // false positive: see installFileObjectInputFilter, called by the NetworkAssi*Engines
    private static void initHelper(ObjectInputStream in, NetworkClipboardFilesHelper helper) throws IOException, ClassNotFoundException {
        helper.setTransferId(UUID.randomUUID().toString());
        helper.setFileMetadatas((ArrayList<FileMetaData>) in.readObject());
        if (helper.getFileMetadatas().isEmpty()) {
            Log.error("Received empty file metadata");
            throw new IOException("Received empty file metadata");
        }
        helper.setFileBytesLeft(helper.getFileMetadatas().get(0).getFileSize());
        helper.setTotalFileBytesLeft(helper.getFileMetadatas().stream().mapToLong(FileMetaData::getFileSize).sum());
        if (helper.getTotalFileBytesLeft() < 0 || helper.getTotalFileBytesLeft() > Integer.MAX_VALUE) {
            Log.error("Received invalid total file size " + helper.getTotalFileBytesLeft());
            throw new IOException("Received invalid total file size " + helper.getTotalFileBytesLeft());
        }
    }

    private static long getRemainingTotalFilesSize(NetworkClipboardFilesHelper helper, int read, int position) throws IOException {
        long remainingFileSize = helper.getFileBytesLeft() - read;
        long remainingTotalFilesSize = helper.getTotalFileBytesLeft() - read;
        if (remainingFileSize <= 0) {
            helper.closeFileOutputStream();
            if (remainingTotalFilesSize > 0) {
                helper.setPosition(++position);
                remainingFileSize = helper.getFileMetadatas().get(position).getFileSize();
            }
        }
        helper.setFileBytesLeft(remainingFileSize);
        helper.setTotalFileBytesLeft(remainingTotalFilesSize);
        return remainingTotalFilesSize;
    }

    private static void writeToTempFile(NetworkClipboardFilesHelper helper, byte[] buffer, int length, String fileName, String tmpDir) throws IOException {
        FileOutputStream stream = helper.getFileOutputStream();
        if (stream == null) {
            final Path rootPath = getRootPath(helper, tmpDir);
            final Path targetPath = Paths.get(tmpDir, helper.getTransferId(), fileName).toAbsolutePath().normalize();
            if (!targetPath.startsWith(rootPath)) {
                throw new IOException("Path escapes transfer directory");
            }
            final Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                try {
                    Files.setPosixFilePermissions(parent, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
                } catch (UnsupportedOperationException ex) {
                    // ignore, not a posix file system
                }
            }
            stream = new FileOutputStream(targetPath.toFile());
            helper.setFileOutputStream(stream);
        }
        stream.write(buffer, 0, length);
    }

    private static Path getRootPath(NetworkClipboardFilesHelper helper, String tmpDir) {
        Path rootPath = helper.getRootPath();
        if (rootPath == null) {
            rootPath = Paths.get(tmpDir, helper.getTransferId()).toAbsolutePath().normalize();
            helper.setRootPath(rootPath);
        }
        return rootPath;
    }

    private List<FileMetaData> getMetaData(List<File> files, String basePath) {
        List<FileMetaData> metas = new ArrayList<>();
        files.forEach(file -> metas.add(new FileMetaData(file.getPath(), file.length(), basePath)));
        return metas;
    }

    public List<File> getFiles() {
        return files;
    }

    /**
     * @return true when no readable file is left to transfer
     */
    public boolean isEmpty() {
        return readableFiles.isEmpty();
    }

    @Override
    public NetworkMessageType getType() {
        return NetworkMessageType.CLIPBOARD_FILES;
    }

    @Override
    public int getWireSize() {
        return 1 + remainingTotalFilesSize.intValue();  // type (byte) + files
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, getType());
        out.writeObject(this.fileMetaDatas);
        for (File file : this.readableFiles) {
            sendFile(file, out);
        }
    }

    private void sendFile(File file, ObjectOutputStream out) throws IOException {
        long fileSize = file.length();
        Log.info("Sending " + file.getName());
        Log.debug("Bytes to be sent: " + fileSize);
        byte[] buffer = new byte[MAX_SEND_BUFFER_CAPACITY];
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            int read;
            long remainingSize = fileSize;
            while ((read = bis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                remainingSize -= read;
                Log.debug(format("FileSize: %d left: %d", fileSize, remainingSize));
            }
        } finally {
            out.flush();
        }
    }

    @Override
    public String toString() {
        return "Clipboard transfer";
    }
}
