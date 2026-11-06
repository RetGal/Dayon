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
import static java.util.Arrays.copyOf;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    private final List<FileMetaData> fileMetaDatas;
    private final Long remainingTotalFilesSize;
    private static final int MAX_READ_BUFFER_CAPACITY = 7168;
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
        this.fileMetaDatas = getMetaData(files, basePath);
        this.remainingTotalFilesSize = remainingTotalFilesSize;
    }

    @SuppressWarnings("squid:S5135") // false positive: see installFileObjectInputFilter, called by the NetworkAssi*Engines
    public static NetworkClipboardFilesHelper unmarshall(ObjectInputStream in, NetworkClipboardFilesHelper helper, String tmpDir) throws IOException {
        try {
            if (helper.getTransferId() == null) {
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
            int position = helper.getPosition();
            FileMetaData meta = helper.getFileMetadatas().get(position);

            long fileSize = meta.getFileSize();
            Log.debug("%s", () -> format("FileSize: %d left: %d", fileSize, helper.getFileBytesLeft()));

            byte[] buffer = new byte[max(0, min(toIntExact(helper.getFileBytesLeft()), MAX_READ_BUFFER_CAPACITY))];
            BufferedInputStream bis = new BufferedInputStream(in);
            int read = bis.read(buffer, 0, buffer.length);
            Log.debug("Bytes read: " + read);
            String fileName = FileUtilities.separatorsToSystem(meta.getFileName());
            fileName = sanitizeFileName(fileName);
            final boolean append = helper.getFileBytesLeft() != fileSize;
            if (!append) {
                Log.info("Receiving " + fileName);
            }
            Path targetPath = Paths.get(tmpDir, helper.getTransferId(), fileName).toAbsolutePath().normalize();
            if (targetPath.toFile().getPath().contains("..")) {
                throw new IOException("Invalid file path: " + targetPath);
            }
            writeToTempFile(buffer, read, targetPath, append);

            if (getRemainingTotalFilesSize(helper, read, position) == 0) {
                Path rootPath = Paths.get(tmpDir, helper.getTransferId()).toAbsolutePath().normalize();
                File[] filesArray = rootPath.toFile().listFiles();
                if (filesArray != null) {
                    helper.setFiles(Arrays.asList(Objects.requireNonNull(filesArray)));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not read clipboard file metadata", e);
        }
        return helper;
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.replace("..", "").replace(File.separator + File.separator, File.separator);
    }

    private static long getRemainingTotalFilesSize(NetworkClipboardFilesHelper helper, int read, int position) {
        long remainingFileSize = helper.getFileBytesLeft() - read;
        long remainingTotalFilesSize = helper.getTotalFileBytesLeft() - read;
        if (remainingFileSize <= 0 && remainingTotalFilesSize > 0) {
            helper.setPosition(++position);
            remainingFileSize = helper.getFileMetadatas().get(position).getFileSize();
        }
        helper.setFileBytesLeft(remainingFileSize);
        helper.setTotalFileBytesLeft(remainingTotalFilesSize);
        return remainingTotalFilesSize;
    }

    private static void writeToTempFile(byte[] buffer, int length, Path tempFilePath, boolean append) throws IOException {
        final Path parent = tempFilePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
            try {
                Files.setPosixFilePermissions(parent, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
            } catch (UnsupportedOperationException ex) {
                // ignore, not a posix file system
            }
        }
        try (FileOutputStream stream = new FileOutputStream(tempFilePath.toFile(), append)) {
            stream.write(copyOf(buffer, length));
        }
    }

    private List<FileMetaData> getMetaData(List<File> files, String basePath) {
        List<FileMetaData> metas = new ArrayList<>();
        files.forEach(file -> extractFileMetaData(file, metas, basePath));
        return metas;
    }

    private void extractFileMetaData(File node, List<FileMetaData> fileMetaDatas, String basePath) {
        if (node.isFile()) {
            fileMetaDatas.add(new FileMetaData(node.getPath(), node.length(), basePath));
        } else if (node.isDirectory()) {
            File[] filesArray = node.listFiles();
            if (filesArray != null) {
                Arrays.stream(filesArray).parallel().forEachOrdered(file -> extractFileMetaData(file, fileMetaDatas, basePath));
            }
        }
    }

    public List<File> getFiles() {
        return files;
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
        for (File file : this.files) {
            processFile(file, out);
        }
    }

    private void processFile(File file, ObjectOutputStream out) throws IOException {
        if (file.isFile()) {
            sendFile(file, out);
        } else {
            File[] filesArray = file.listFiles();
            if (filesArray != null) {
                for (File node : filesArray) {
                    processFile(node, out);
                }
            }
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
