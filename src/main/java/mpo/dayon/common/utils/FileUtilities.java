package mpo.dayon.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class FileUtilities {

    public static long calculateTotalFileSize(List<File> files) throws IOException {
        long totalFilesSize = 0;
        for (File file : files) {
            totalFilesSize += calculateFileSize(file);
        }
        return totalFilesSize;
    }

    private static long calculateFileSize(File node) throws IOException {
        if (node.isFile()) {
            return node.length();
        }
        return Files.walk(node.toPath())
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum();
    }

    public static String separatorsToSystem(String path) {
        if (path == null) return null;
        if (File.separatorChar == '\\') {
            return path.replace('/', File.separatorChar);
        } else {
            return path.replace('\\', File.separatorChar);
        }
    }

}
