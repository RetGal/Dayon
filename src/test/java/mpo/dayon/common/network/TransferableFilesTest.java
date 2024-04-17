package mpo.dayon.common.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class TransferableFilesTest {

    private TransferableFiles transferableFiles;
    @BeforeEach
    void setup() {
        transferableFiles =  new TransferableFiles(new ArrayList<>());
    }

    @Test
    void getTransferDataAsFileList() throws UnsupportedFlavorException {
        final File file = new File(String.valueOf(UUID.randomUUID()));
        transferableFiles = new TransferableFiles(Collections.singletonList(file));
        final List<File> files = (List<File>) transferableFiles.getTransferData(DataFlavor.javaFileListFlavor);
        assertEquals(file, files.get(0));
    }

    @Test
    void getTransferDataAsUri() throws ClassNotFoundException, UnsupportedFlavorException {
        final File file = new File(String.valueOf(UUID.randomUUID()));
        transferableFiles = new TransferableFiles(Collections.singletonList(file));
        final String uri = (String) transferableFiles.getTransferData(new DataFlavor("text/uri-list;class=java.lang.String"));
        assertEquals("x-special/nautilus-clipboard", uri.substring(0, 28));
        assertTrue(uri.contains(file.getName()));
    }

    @Test
    void getTransferDataAsGnomeCopiedFiles() throws ClassNotFoundException, UnsupportedFlavorException {
        final File file = new File(String.valueOf(UUID.randomUUID()));
        transferableFiles = new TransferableFiles(Collections.singletonList(file));
        final ByteArrayInputStream stream = (ByteArrayInputStream) transferableFiles.getTransferData(new DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream"));
        int len = stream.available();
        byte[] bytes = new byte[len];
        stream.read(bytes, 0, len);
        String string = new String(bytes, UTF_8);
        assertEquals("copy", string.substring(0, 4));
        assertTrue(string.contains(file.getName()));
    }

    @Test
    void getTransferDataAsStringShouldThrow() {
        final File file = new File(String.valueOf(UUID.randomUUID()));
        transferableFiles = new TransferableFiles(Collections.singletonList(file));
        assertThrows(UnsupportedFlavorException.class, () -> transferableFiles.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    void getTransferDataFlavors() {
        final DataFlavor[] transferDataFlavors = transferableFiles.getTransferDataFlavors();
        assertEquals(3, transferDataFlavors.length);
    }

    @Test
    void isDataFlavorSupported() throws ClassNotFoundException {
        assertTrue(transferableFiles.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
        assertTrue(transferableFiles.isDataFlavorSupported(new DataFlavor("text/uri-list;class=java.lang.String")));
        assertTrue(transferableFiles.isDataFlavorSupported(new DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream")));
        assertFalse(transferableFiles.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertFalse(transferableFiles.isDataFlavorSupported(DataFlavor.imageFlavor));
    }
}