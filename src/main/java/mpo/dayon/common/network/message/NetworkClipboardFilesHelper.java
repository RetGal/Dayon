package mpo.dayon.common.network.message;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NetworkClipboardFilesHelper {

    private List<File> files;
    private List<String> fileNames;
    private List<Long> fileSizes;
    private int position;
    private long fileBytesLeft;
    private volatile long totalFileBytesLeft ;

    public NetworkClipboardFilesHelper() {
        this.files = new ArrayList<>();
        this.fileNames = new ArrayList<>();
        this.fileSizes = new ArrayList<>();
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

    public List<String> getFileNames() {
        return fileNames;
    }

    public void setFileNames(List<String> fileNames) {
        this.fileNames = fileNames;
    }

    public List<Long> getFileSizes() {
        return fileSizes;
    }

    public void setFileSizes(List<Long> fileSizes) {
        this.fileSizes = fileSizes;
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

    public boolean isIdle() {
        return totalFileBytesLeft == 0;
    }
}
