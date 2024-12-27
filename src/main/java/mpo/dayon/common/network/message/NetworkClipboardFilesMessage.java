package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.FileUtilities;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.util.Arrays.copyOf;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    private final List<FileMetaData> fileMetaDatas;
    private final Long remainingTotalFilesSize;
    private static final int MAX_READ_BUFFER_CAPACITY = 7168;
    private static final int MAX_SEND_BUFFER_CAPACITY = 1048576;

    public NetworkClipboardFilesMessage(List<File> files, long remainingTotalFilesSize, String basePath) {
        this.files = Collections.unmodifiableList(files);
        this.fileMetaDatas = getMetaData(files, basePath);
        this.remainingTotalFilesSize = remainingTotalFilesSize;
    }

    @java.lang.SuppressWarnings("squid:S5135") // assistant and assisted trust each other
    public static NetworkClipboardFilesHelper unmarshall(ObjectInputStream in, NetworkClipboardFilesHelper helper, String tmpDir) throws IOException {
        try {
            if (helper.getTransferId() == null) {
                helper.setTransferId(UUID.randomUUID().toString());
                helper.setFileMetadatas((ArrayList<FileMetaData>) in.readObject());
                helper.setFileBytesLeft(helper.getFileMetadatas().get(0).getFileSize());
                helper.setTotalFileBytesLeft(helper.getFileMetadatas().stream().mapToLong(FileMetaData::getFileSize).sum());
            }
            int position = helper.getPosition();
            FileMetaData meta = helper.getFileMetadatas().get(position);

            long fileSize = meta.getFileSize();
            Log.debug("%s", () -> format("FileSize: %d left: %d", fileSize, helper.getFileBytesLeft()));

            byte[] buffer = new byte[min(toIntExact(helper.getFileBytesLeft()), MAX_READ_BUFFER_CAPACITY)];
            BufferedInputStream bis = new BufferedInputStream(in);
            int read = bis.read(buffer, 0, buffer.length);
            Log.debug("Bytes read: " + read);
            String fileName = FileUtilities.separatorsToSystem(meta.getFileName());
            final boolean append = helper.getFileBytesLeft() != fileSize;
            if (!append) {
                Log.info("Receiving " + fileName);
            }
            if (!tmpDir.endsWith(File.separator)) {
                tmpDir += File.separator;
            }
            String tempFilePath = format("%s%s%s", tmpDir, helper.getTransferId(), fileName);
            writeToTempFile(buffer, read, tempFilePath, append);

            if (getRemainingTotalFilesSize(helper, read, position) == 0) {
                String rootPath = format("%s%s", tmpDir, helper.getTransferId());
                File[] filesArray = new File(rootPath).listFiles();
                if (filesArray != null) {
                    helper.setFiles(Arrays.asList(Objects.requireNonNull(filesArray)));
                }
            }
        } catch (ClassNotFoundException e) {
            Log.error(e.getMessage());
        }
        return helper;
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

    private static void writeToTempFile(byte[] buffer, int length, String tempFileName, boolean append) throws IOException {
        final Path parent = Paths.get(tempFileName).getParent();
        if (parent != null && !Files.exists(parent)) {
            final boolean created = parent.toFile().mkdirs();
            if (!created) {
                Log.error("Could not create parent directories for " + tempFileName);
            }
        }
        try (FileOutputStream stream = new FileOutputStream(tempFileName, append)) {
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
