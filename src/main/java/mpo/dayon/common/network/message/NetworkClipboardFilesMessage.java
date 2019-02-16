package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.copyOf;

public class NetworkClipboardFilesMessage extends NetworkMessage {

    private final List<File> files;
    private final Long size;
    private String fileName;

    public NetworkClipboardFilesMessage(List<File> files, long size, String fileName) {
        this.files = files;
        this.size = size;
        this.fileName = fileName;
    }

    public static NetworkClipboardFilesMessage unmarshall(ObjectInputStream in, String fileName, long fileSize) throws IOException {

        List<File> files = new LinkedList<>();
        int read = 0;
        try {
            if (fileName.isEmpty()) {
                fileName = (String) in.readObject();
                fileSize = in.readLong();
            }
            byte[] buffer = new byte[Math.toIntExact(fileSize)];
            int chunk = 1024;
            read = in.read(buffer, 0, 1);
            while(in.available() > 0) {
                chunk = chunk > in.available() ? in.available() : chunk;
                read += in.read(buffer, read, chunk);
            }
            File tempFile = File.createTempFile(fileName, "tmp");
            String tempPath = tempFile.getParent() + File.separator + fileName;
            tempFile.delete();

            try (FileOutputStream stream = new FileOutputStream(tempPath,true)) {
                stream.write(copyOf(buffer, read));
            }
            files.add(new File(tempPath));

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        long remaining = fileSize-read;
        Log.debug("Received File: " + fileName + " size/read/remaining " + fileSize + "/" + read + "/" + remaining);
        return new NetworkClipboardFilesMessage(files, remaining, fileName);
    }

    public File getFile() {
        return files.get(0);
    }

    public boolean isDone() { return size == 0; }

    public String getFileName() { return fileName; }

    @Override
    public NetworkMessageType getType() {
        return NetworkMessageType.CLIPBOARD_FILES;
    }

    @Override
    public int getWireSize() {
        return 1 + size.intValue();  // type (byte) + files
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, NetworkMessageType.class, getType());
        List<File> files = (List) this.files;
        for (File file : files) {
            handleFile(file, out);
        }
    }

    private void handleFile(File file, ObjectOutputStream out) throws IOException {
        if (file.isDirectory()) {
            for (File aFile : file.listFiles()) {
                handleFile(aFile, out);
            }
        }
        out.writeObject(file.getName());
        long fileSize = file.length();
        out.writeLong(fileSize);
        byte[] buffer = new byte[Math.toIntExact(fileSize)];
        InputStream input = new FileInputStream(file);
        int read = 0;
        int chunk = 1024;
        while(input.available() > 0) {
            chunk = chunk > input.available() ? input.available() : chunk;
            read += input.read(buffer, read, chunk);
        }
        out.write(buffer);
    }

    @Override
    public String toString() {
        return "Clipboard transfer";
    }
}
