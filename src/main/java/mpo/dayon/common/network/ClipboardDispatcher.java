package mpo.dayon.common.network;

import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.log.Log;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;
import static java.lang.String.valueOf;

public class ClipboardDispatcher {

    private ClipboardDispatcher() {
    }

    public static void sendClipboard(NetworkEngine networkEngine, BaseFrame frame, ClipboardOwner owner) {

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable content = clipboard.getContents(owner);
        if (content == null) return;

        try {
            if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // noinspection unchecked
                List<File> files = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    final long totalFilesSize = FileUtilities.calculateTotalFileSize(files);
                    Log.debug("Clipboard contains files with size: %s", () -> valueOf(totalFilesSize));
                    // Ok as very few of that (!)
                    new Thread(() -> networkEngine.sendClipboardFiles(files, totalFilesSize, files.get(0).getParent()), "sendClipboardFiles").start();
                    frame.onClipboardSending();
                    return;
                }
            } else if (content.isDataFlavorSupported(DataFlavor.imageFlavor) ){
                final BufferedImage image = (BufferedImage) clipboard.getData(DataFlavor.imageFlavor);
                Log.debug("Clipboard contains graphics: %s", () -> format("%dx%d", image.getWidth(), image.getHeight()));
                // Ok as very few of that (!)
                new Thread(() -> networkEngine.sendClipboardGraphic(new TransferableImage(image)), "sendClipboardGraphic").start();
                frame.onClipboardSending();
                return;
            }  else if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = valueOf(clipboard.getData(DataFlavor.stringFlavor));
                Log.debug("Clipboard contains text: " + text);
                // Ok as very few of that (!)
                new Thread(() -> networkEngine.sendClipboardText(text), "sendClipboardText").start();
                frame.onClipboardSending();
                return;
            } else {
                Log.debug("Clipboard contains no supported data");
            }
        } catch (IOException | UnsupportedFlavorException ex) {
            Log.error("Clipboard error " + ex.getMessage());
            frame.onClipboardSent();
        }
        String text = "\uD83E\uDD84";
        Log.debug("Sending a unicorn: " + text);
        networkEngine.sendClipboardText(text);
    }
}
