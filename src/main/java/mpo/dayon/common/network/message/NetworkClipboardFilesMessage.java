package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.FileUtilities;

import java.io.*;
import java.util.*;

import static java.util.Arrays.copyOf;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    private final List<FileMetaData> fileMetaDatas;
    private final int position;
    private final Long remainingFileSize;
    private final Long remainingTotalFilesSize;
    private static final int MAX_BUFFER_CAPACITY = 7168;

    public NetworkClipboardFilesMessage(List<File> files, long remainingTotalFilesSize, String basePath) {
        this.files = files;
        this.fileMetaDatas = getMetaData(files, basePath);
        this.position = 0;
        this.remainingFileSize = fileMetaDatas.get(0).getFileSize();
        this.remainingTotalFilesSize = remainingTotalFilesSize;
    }

    public static NetworkClipboardFilesMessage unmarshall(ObjectInputStream in, NetworkClipboardFilesHelper helper) throws IOException {

        try {
            if (helper.getTransferId() == null) {
                helper.setTransferId(UUID.randomUUID().toString());
                helper.setFileMetadatas((ArrayList<FileMetaData>) in.readObject());
                helper.setFileBytesLeft(helper.getFileMetadatas().get(0).getFileSize());
                helper.setTotalFileBytesLeft(helper.getFileMetadatas().stream().mapToInt(fileMetaData -> (int) fileMetaData.getFileSize()).sum());
            }
            int position = helper.getPosition();
            FileMetaData meta = helper.getFileMetadatas().get(position);

            String fileName = FileUtilities.separatorsToSystem(meta.getFileName());
            long fileSize = meta.getFileSize();
            byte[] buffer;
            Log.debug("Size/written: " + Math.toIntExact(fileSize) + "/" + helper.getFileBytesLeft());
            buffer = helper.getFileBytesLeft() < MAX_BUFFER_CAPACITY ? new byte[Math.toIntExact(helper.getFileBytesLeft())] : new byte[MAX_BUFFER_CAPACITY];

            int read = readIntoBuffer(in, buffer);
            final boolean append = helper.getFileBytesLeft() != fileSize;
            if (!append) {
                Log.info("Received " + meta.getFileName());
            }
            String tempFilePath = System.getProperty("java.io.tmpdir") + File.separator + helper.getTransferId() + fileName;
            writeToTempFile(buffer, read, tempFilePath, append);

            long remainingFileSize = helper.getFileBytesLeft() - read;
            long remainingTotalFilesSize = helper.getTotalFileBytesLeft() - read;
            if (remainingFileSize <= 0 && remainingTotalFilesSize > 0) {
                position++;
                remainingFileSize = helper.getFileMetadatas().get(position).getFileSize();
            }
            helper.setFileBytesLeft(remainingFileSize);
            helper.setTotalFileBytesLeft(remainingTotalFilesSize);
            helper.setPosition(position);

            if (remainingTotalFilesSize == 0) {
                String rootPath = System.getProperty("java.io.tmpdir") + File.separator + helper.getTransferId();
                helper.setFiles(Arrays.asList(new File(rootPath).listFiles()));
            }

        } catch (ClassNotFoundException e) {
            Log.error(e.getMessage());
        }

        return new NetworkClipboardFilesMessage(helper);
    }

    private static int readIntoBuffer(InputStream in, byte[] buffer) throws IOException {
        int chunk;
        int read = in.read(buffer, 0, 1);
        while (in.available() > 0 && read < buffer.length) {
            chunk = Math.min(in.available(), buffer.length - read);
            read += in.read(buffer, read, chunk);
        }
        return read;
    }

    private static void writeToTempFile(byte[] buffer, int length, String tempFileName, boolean append) throws IOException {
        new File(tempFileName.substring(0, tempFileName.lastIndexOf(File.separatorChar))).mkdirs();
        try (FileOutputStream stream = new FileOutputStream(tempFileName, append)) {
            stream.write(copyOf(buffer, length));
        }
    }

    private NetworkClipboardFilesMessage(NetworkClipboardFilesHelper helper) {
        this.files = helper.getFiles();
        this.fileMetaDatas = helper.getFileMetadatas();
        this.position = helper.getPosition();
        this.remainingFileSize = helper.getFileBytesLeft();
        this.remainingTotalFilesSize = helper.getTotalFileBytesLeft();
    }

    private List<FileMetaData> getMetaData(List<File> files, String basePath) {
        List<FileMetaData> fileMetaDatas = new ArrayList<>();
        for (File file : files) {
            extractFileMetaData(file, fileMetaDatas, basePath);
        }
        return fileMetaDatas;
    }

    private void extractFileMetaData(File node, List<FileMetaData> fileMetaDatas, String basePath) {
        if (node.isFile()) {
            fileMetaDatas.add(new FileMetaData(node.getPath(), node.length(), basePath));
        }
        if (node.isDirectory()) {
            for (File file : Objects.requireNonNull(node.listFiles())) {
                extractFileMetaData(file, fileMetaDatas, basePath);
            }
        }
    }

    public List<File> getFiles() {
        return files;
    }

    public List<FileMetaData> getFileMetaDatas() {
        return fileMetaDatas;
    }

    public int getPosition() {
        return position;
    }

    public Long getRemainingFileSize() {
        return remainingFileSize;
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
        // ArrayList implements serializable, other List implementations might not..
        out.writeObject((ArrayList<FileMetaData>) this.fileMetaDatas);
        for (File file : this.files) {
            processFile(file, out);
        }
    }

    private void processFile(File file, ObjectOutputStream out) throws IOException {
        if (file.isFile()) {
            sendFile(file, out);
        } else {
            for (File node : Objects.requireNonNull(file.listFiles())) {
                if (node.isFile()) {
                    sendFile(node, out);
                } else {
                    processFile(node, out);
                }
            }
        }
    }

    private void sendFile(File file, ObjectOutputStream out) throws IOException {
        long fileSize = file.length();
        Log.debug("Total bytes to be sent: " + fileSize);
        byte[] buffer = fileSize < MAX_BUFFER_CAPACITY ? new byte[Math.toIntExact(fileSize)] : new byte[MAX_BUFFER_CAPACITY];
        try (InputStream input = new FileInputStream(file)) {
            int read;
            while (input.available() > 0) {
                read = readIntoBuffer(input, buffer);
                out.write(copyOf(buffer, read));
                Log.debug("Bytes sent: " + read);
            }
            out.flush();
        }
    }

    @Override
    public String toString() {
        return "Clipboard transfer";
    }
}
