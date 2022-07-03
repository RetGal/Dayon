package mpo.dayon.common.network.message;

import java.io.Serializable;
import java.util.Objects;

class FileMetaData implements Serializable {
    private final String fileName;
    private final long fileSize;

    FileMetaData(String fileName, long fileSize, String basePath) {
        this.fileName = fileName.replace(basePath, "");
        this.fileSize = fileSize;
    }

    String getFileName() {
        return fileName;
    }

    long getFileSize() {
        return fileSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileMetaData that = (FileMetaData) o;

        if (fileSize != that.getFileSize()) return false;
        return Objects.equals(fileName, that.getFileName());
    }

    @Override
    public int hashCode() {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (int) (fileSize ^ (fileSize >>> 32));
        return result;
    }
}
