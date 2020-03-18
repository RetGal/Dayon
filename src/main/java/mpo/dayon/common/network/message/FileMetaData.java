package mpo.dayon.common.network.message;

import java.io.Serializable;

public class FileMetaData implements Serializable {
    private String fileName;
    private long fileSize;

    public FileMetaData(String fileName, long fileSize, String basePath) {
        this.fileName = fileName.replace(basePath, "");
        this.fileSize = fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileMetaData that = (FileMetaData) o;

        if (fileSize != that.fileSize) return false;
        return fileName != null ? fileName.equals(that.fileName) : that.fileName == null;
    }

    @Override
    public int hashCode() {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (int) (fileSize ^ (fileSize >>> 32));
        return result;
    }
}
