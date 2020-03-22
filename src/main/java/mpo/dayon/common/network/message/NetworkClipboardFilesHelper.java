package mpo.dayon.common.network.message;

import java.io.File;
import java.util.ArrayList;
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
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }

    public List<FileMetaData> getFileMetadatas() {
        return fileMetadatas;
    }

    public void setFileMetadatas(List<FileMetaData> fileMetadatas) {
        this.fileMetadatas = fileMetadatas;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getFileBytesLeft() {
        return fileBytesLeft;
    }

    public void setFileBytesLeft(long fileBytesLeft) {
        this.fileBytesLeft = fileBytesLeft;
    }

    public long getTotalFileBytesLeft() {
        return totalFileBytesLeft;
    }

    public void setTotalFileBytesLeft(long totalFileBytesLeft) {
        this.totalFileBytesLeft = totalFileBytesLeft;
    }

    public boolean isDone() {
        return totalFileBytesLeft == 0;
    }
}
