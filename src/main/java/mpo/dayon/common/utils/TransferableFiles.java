package mpo.dayon.common.utils;

import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class TransferableFiles implements Transferable {

    private final List<File> files;
    private static DataFlavor uriListFlavor;
    private static DataFlavor gnomeCopiedFilesFlavor;

    static {
        try {
            gnomeCopiedFilesFlavor = new DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream");
            uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            Log.error(e.getMessage()); // this should not happen
        }
    }

    private static final DataFlavor[] FLAVORS = new DataFlavor[]{
            DataFlavor.javaFileListFlavor, gnomeCopiedFilesFlavor, uriListFlavor};


    public TransferableFiles(List<File> files) {
        this.files = files;
    }

    @NotNull
    @Override
    public Object getTransferData(DataFlavor flavor) throws
            UnsupportedFlavorException {
        Log.debug("getTransferData " + flavor.toString());
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            return files;
        } else if (flavor.equals(gnomeCopiedFilesFlavor)) {
            return toGnomeCopiedFilesFlavor();
        } else if (flavor.equals(uriListFlavor)) {
            return toUriListFlavor();
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

    @NotNull
    private Object toUriListFlavor() {
        final StringBuilder sb = new StringBuilder();
        files.forEach(file -> sb.append(file.toURI()).append("\r\n"));
        return sb.toString();
    }

    @NotNull
    private Object toGnomeCopiedFilesFlavor() {
        final StringBuilder sb = new StringBuilder("copy\n");
        files.forEach(file -> sb.append(file.toURI()).append("\n"));
        sb.delete(sb.length() - 1, sb.length());
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return Arrays.stream(FLAVORS).anyMatch(df -> df.equals(flavor));
    }

}
