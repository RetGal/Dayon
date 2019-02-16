package mpo.dayon.common.utils;

import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.util.Arrays;
import java.util.List;

public class TransferableFiles implements Transferable {

    private final List<File> files;
    private static DataFlavor uriListFlavor;
    static {
        try {
            uriListFlavor = new
                    DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            Log.error(e.getMessage()); // can't happen
        }
    }

    private static DataFlavor[] FLAVORS = new DataFlavor[] {
            DataFlavor.javaFileListFlavor, uriListFlavor };


    public TransferableFiles(List<File> files) {
        this.files = files;
    }

    @NotNull
    @Override
    public Object getTransferData(DataFlavor flavor) throws
            UnsupportedFlavorException, java.io.IOException {
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            return files;
        } else if (flavor.equals(uriListFlavor)) {
            // refer to RFC 2483 for the text/uri-list format
            return files.get(0).toURI() + "\r\n";
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

    @Override
    public DataFlavor[] getTransferDataFlavors(){
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor){
        return Arrays.stream(FLAVORS).anyMatch(df -> df.equals(flavor));
    }

}
