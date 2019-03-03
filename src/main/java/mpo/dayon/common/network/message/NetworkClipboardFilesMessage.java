package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.copyOf;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    // ArrayList implements serializable, other List implementations might not..
    private final ArrayList<String> fileNames;
    private final ArrayList<Long> fileSizes;
    private final int position;
    private final Long remainingFileSize;
    private final Long remainingTotalFilesSize;

    public NetworkClipboardFilesMessage(List<File> files, long remainingTotalFilesSize) {
        this.files = files;
        this.fileNames = (ArrayList<String>) files.stream().map(File::getName).collect(Collectors.toList());
        this.fileSizes = (ArrayList<Long>) files.stream().map(File::length).collect(Collectors.toList());
        this.position = 0;
        this.remainingFileSize = files.get(0).length();
        this.remainingTotalFilesSize = remainingTotalFilesSize;
    }

    public static NetworkClipboardFilesMessage unmarshall(ObjectInputStream in, NetworkClipboardFilesHelper helper) throws IOException {

        try {
            if (helper.getFileNames().isEmpty()) {
                helper.setFileNames((ArrayList) in.readObject());
                helper.setFileSizes((ArrayList) in.readObject());
                helper.setFileBytesLeft(helper.getFileSizes().get(0));
                helper.setTotalFileBytesLeft(helper.getFileSizes().stream().mapToInt(Long::intValue).sum());
            }
            byte[] buffer;
            int position = helper.getPosition();
            String fileName = helper.getFileNames().get(position);
            Long fileSize = helper.getFileSizes().get(position);
            if (helper.getFiles().size() == position) {
                Log.debug("Received File/size: " + fileName + "/" + fileSize);
                buffer = new byte[Math.toIntExact(fileSize)];
            } else {
                Log.info("Size/written: " + Math.toIntExact(fileSize) + "/" + helper.getFiles().get(position).length());
                buffer = new byte[Math.toIntExact(helper.getFileBytesLeft())];
            }

            int read = readIntoBuffer(in, buffer);
            String tempFilePath = writeToTempFile(buffer, read, fileName);

            if (helper.getFiles().size() == position) {
                helper.getFiles().add(new File(tempFilePath));
            } else {
                helper.getFiles().set(position, new File(tempFilePath));
            }
            long remainingFileSize = helper.getFileBytesLeft() - read;
            long remainingTotalFilesSize = helper.getTotalFileBytesLeft() - read;

            if (remainingFileSize <= 0 && remainingTotalFilesSize > 0) {
                position++;
                remainingFileSize = helper.getFileSizes().get(position);
            }
            helper.setFileBytesLeft(remainingFileSize);
            helper.setTotalFileBytesLeft(remainingTotalFilesSize);
            helper.setPosition(position);

        } catch (ClassNotFoundException e) {
            Log.error(e.getMessage());
        }

        return new NetworkClipboardFilesMessage(helper);
    }

    private static int readIntoBuffer(ObjectInputStream in, byte[] buffer) throws IOException {
        int chunk = 1024;
        int read = in.read(buffer, 0, 1);
        while (in.available() > 0 && read < buffer.length) {
            chunk = chunk > buffer.length - read ? buffer.length - read : chunk;
            chunk = chunk > in.available() ? in.available() : chunk;
            read += in.read(buffer, read, chunk);
        }
        return read;
    }

    @NotNull
    private static String writeToTempFile(byte[] buffer, int length, String fileName) throws IOException {
        String tempFilePath = System.getProperty("java.io.tmpdir") + File.separator + fileName;

        boolean append = appendFile(tempFilePath);

        try (FileOutputStream stream = new FileOutputStream(tempFilePath, append)) {
            stream.write(copyOf(buffer, length));
            Log.debug("Bytes written: " + length);
        }
        return tempFilePath;
    }

    private static boolean appendFile(String tempFilePath) {
        File existingFile = new File(tempFilePath);
        return (existingFile.isFile() && existingFile.lastModified() > System.currentTimeMillis()-2000);
    }

    private NetworkClipboardFilesMessage(NetworkClipboardFilesHelper helper) {
        this.files = helper.getFiles();
        this.fileNames = helper.getFileNames();
        this.fileSizes = helper.getFileSizes();
        this.position = helper.getPosition();
        this.remainingFileSize = helper.getFileBytesLeft();
        this.remainingTotalFilesSize = helper.getTotalFileBytesLeft();
    }

    public List<File> getFiles() {
        return files;
    }

    public ArrayList<String> getFileNames() { return fileNames; }

    public ArrayList<Long> getFileSizes() {
        return fileSizes;
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
        marshallEnum(out, NetworkMessageType.class, getType());
        out.writeObject(this.fileNames);
        out.writeObject(this.fileSizes);
        for (File file : this.files) {
            sendFile(file, out);
        }
    }

    private void sendFile(File file, ObjectOutputStream out) throws IOException {
        long fileSize = file.length();
        byte[] buffer = new byte[Math.toIntExact(fileSize)];
        try (InputStream input = new FileInputStream(file)) {
            int read = 0;
            int chunk = 1024;
            while (input.available() > 0) {
                chunk = chunk > input.available() ? input.available() : chunk;
                read += input.read(buffer, read, chunk);
            }
            Log.debug("Bytes sent: " + read);
            out.write(buffer);
        }
    }

    @Override
    public String toString() {
        return "Clipboard transfer";
    }
}
