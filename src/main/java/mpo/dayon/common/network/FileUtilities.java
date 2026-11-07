package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class FileUtilities {

    private FileUtilities() {
        throw new IllegalStateException();
    }

    /**
     * Returns all readable regular files contained in the given selection.
     * <p/>
     * Directories are traversed recursively. Files that cannot be read (e.g.
     * hidden files in a snap-confined home directory) are skipped.
     */
    public static List<File> getReadableFiles(List<File> files) {
        final List<File> readableFiles = new ArrayList<>();
        for (File file : files) {
            collectReadableFiles(file, readableFiles);
        }
        return readableFiles;
    }

    private static void collectReadableFiles(File node, List<File> readableFiles) {
        if (node.isFile()) {
            if (isReadable(node)) {
                readableFiles.add(node);
            } else {
                Log.debug("Clipboard file is not readable and will be skipped: " + node.getAbsolutePath());
            }
        } else if (node.isDirectory()) {
            final File[] filesArray = node.listFiles();
            if (filesArray != null) {
                for (File file : filesArray) {
                    collectReadableFiles(file, readableFiles);
                }
            }
        }
    }

    /**
     * Checks the actual read access instead of relying on the file attributes only:
     * a sandbox (e.g. snap) might allow stat() but deny open().
     */
    private static boolean isReadable(File file) {
        if (!file.canRead()) {
            return false;
        }
        try (InputStream ignored = Files.newInputStream(file.toPath())) {
            return true;
        } catch (IOException | SecurityException ex) {
            return false;
        }
    }

    /**
     * Sums the size of the given files. Callers are expected to pass the
     * readable files returned by {@link #getReadableFiles(List)}.
     */
    public static long calculateTotalFileSize(List<File> files) {
        long totalFilesSize = 0;
        for (File file : files) {
            totalFilesSize += file.length();
        }
        return totalFilesSize;
    }

    public static String separatorsToSystem(String path) {
        if (path == null) return null;
        if (File.separatorChar == '\\') {
            return path.replace('/', File.separatorChar);
        }
        return path.replace('\\', File.separatorChar);
    }

}
