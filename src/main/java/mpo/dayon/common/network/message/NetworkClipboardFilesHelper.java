package mpo.dayon.common.network.message;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkClipboardFilesHelper {

    private List<File> files;
    private List<FileMetaData> fileMetadatas;
    private String transferId;
    private volatile int position;
    private volatile long fileBytesLeft;
    private volatile long totalFileBytesLeft ;

    public NetworkClipboardFilesHelper() {
        this.files = new ArrayList<>();
        this.fileMetadatas = new ArrayList<>();
        this.position = 0;
        this.fileBytesLeft = 0;
        this.totalFileBytesLeft = 0;
    }

    public List<File> getFiles() {
        return Collections.unmodifiableList(files);
    }

    public void setFiles(List<File> files) {
        this.files = Collections.unmodifiableList(files);
    }

    List<FileMetaData> getFileMetadatas() {
        return Collections.unmodifiableList(fileMetadatas);
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
        return totalFileBytesLeft == 0;
    }
}
