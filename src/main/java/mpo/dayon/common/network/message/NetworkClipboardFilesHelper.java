package mpo.dayon.common.network.message;

import mpo.dayon.common.log.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class NetworkClipboardFilesHelper {

    static final int MAX_CHUNK_SIZE = 1 << 20;
    static final int WRITE_BUFFER_SIZE = 64 * 1024;

    private List<File> files = Collections.emptyList();
    private List<FileMetaData> fileMetadatas = Collections.emptyList();
    private String transferId;
    private volatile int position;
    private volatile long fileBytesLeft;
    private volatile long totalFileBytesLeft;
    private OutputStream fileOutputStream;
    private Path rootPath;
    private byte[] readBuffer;

    public NetworkClipboardFilesHelper() {
        this.position = 0;
        this.fileBytesLeft = 0;
        this.totalFileBytesLeft = 0;
    }

    public List<File> getFiles() {
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = Collections.unmodifiableList(files);
    }

    List<FileMetaData> getFileMetadatas() {
        return fileMetadatas;
    }

    void setFileMetadatas(List<FileMetaData> fileMetadatas) {
        this.fileMetadatas = Collections.unmodifiableList(fileMetadatas);
    }

    String getTransferId() {
        return transferId;
    }

    void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    long getFileBytesLeft() {
        return fileBytesLeft;
    }

    void setFileBytesLeft(long fileBytesLeft) {
        this.fileBytesLeft = fileBytesLeft;
    }

    long getTotalFileBytesLeft() {
        return totalFileBytesLeft;
    }

    void setTotalFileBytesLeft(long totalFileBytesLeft) {
        this.totalFileBytesLeft = totalFileBytesLeft;
    }

    public boolean isDone() {
        return transferId == null || position >= fileMetadatas.size();
    }

    OutputStream getFileOutputStream() {
        return fileOutputStream;
    }

    void setFileOutputStream(OutputStream fileOutputStream) {
        this.fileOutputStream = fileOutputStream;
    }

    Path getRootPath() {
        return rootPath;
    }

    void setRootPath(Path rootPath) {
        this.rootPath = rootPath;
    }

    byte[] getReadBuffer() {
        if (readBuffer == null) {
            readBuffer = new byte[MAX_CHUNK_SIZE];
        }
        return readBuffer;
    }

    void closeFileOutputStream() throws IOException {
        final OutputStream stream = fileOutputStream;
        if (stream != null) {
            try {
                stream.close();
            } finally {
                fileOutputStream = null;
            }
        }
    }

    public void close() {
        try {
            closeFileOutputStream();
        } catch (IOException ex) {
            Log.error("Could not close clipboard file stream", ex);
        }
    }
}
