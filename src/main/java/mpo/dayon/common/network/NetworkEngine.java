package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.*;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.utils.SystemUtilities.*;

/**
 * Both the assistant and the assisted are talking to each other using a very
 * simple asynchronous network message layer. The network engine is handling
 * both the sending and the receiving sides.
 */
public abstract class NetworkEngine {

    protected static final String UNSUPPORTED_TYPE = "Unsupported message type [%s]!";

    private static final String CLIPBOARD_DEBUG = "setClipboardContents %s";

    protected NetworkSender sender; // out

    protected NetworkSender fileSender; // file out

    protected Thread receiver; // in

    protected ObjectInputStream in;

    protected Thread fileReceiver; // file in

    protected ObjectInputStream fileIn;

    protected SSLServerSocket server;

    protected SSLSocket connection;

    protected SSLServerSocket fileServer;

    protected SSLSocket fileConnection;

    protected final AtomicBoolean cancelling = new AtomicBoolean(false);

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendClipboardText(String text) {
        if (sender != null) {
            String utf8Encoded = UTF_8.decode(UTF_8.encode(text)).toString();
            sender.sendClipboardContentText(utf8Encoded, utf8Encoded.getBytes().length);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendClipboardGraphic(TransferableImage image) {
        if (sender != null) {
            sender.sendClipboardContentGraphic(image);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size, basePath);
        }
    }

    protected static void setClipboardContents(String string, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> string);
        StringSelection stringSelection = new StringSelection(string);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, clipboardOwner);
    }

    public static void setClipboardContents(BufferedImage image, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> format("%dx%d", image.getWidth(), image.getHeight()));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(image), clipboardOwner);
    }

    private static void setClipboardContents(List<File> files, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> String.valueOf(files));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableFiles(files), clipboardOwner);
    }

    private static NetworkClipboardFilesHelper handleNetworkClipboardFilesHelper(NetworkClipboardFilesHelper filesHelper, ClipboardOwner clipboardOwner) {
        if (filesHelper.isDone()) {
            setClipboardContents(filesHelper.getFiles(), clipboardOwner);
            return new NetworkClipboardFilesHelper();
        }
        return filesHelper;
    }

    protected void initSender(int queueSize) throws IOException {
        sender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream())));
        sender.start(queueSize);
        sender.ping();
    }

    protected void initFileSender() throws IOException {
        fileSender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream())));
        fileSender.start(1);
        fileSender.ping();
    }

    protected void handleIncomingClipboardFiles(ObjectInputStream fileIn, ClipboardOwner clipboardOwner) throws IOException {
        String tmpDir = getTempDir();
        NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

        //noinspection InfiniteLoopStatement
        while (true) {
            NetworkMessageType type;
            if (filesHelper.isDone()) {
                NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                Log.debug("Received " + type.name());
                if (!type.equals(CLIPBOARD_FILES) && !type.equals(PING)) {
                    throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
                }
            } else {
                type = CLIPBOARD_FILES;
            }

            if (type.equals(CLIPBOARD_FILES)) {
                filesHelper = handleNetworkClipboardFilesHelper(NetworkClipboardFilesMessage.unmarshall(fileIn,
                        filesHelper, tmpDir), clipboardOwner);
                if (filesHelper.isDone()) {
                    fireOnClipboardReceived();
                }
            }
        }
    }

    protected void fireOnClipboardReceived() {
    }

    protected void closeConnections() {
        if (sender != null) {
            sender.cancel();
        }
        receiver = safeInterrupt(receiver);
        safeClose(in, connection, server);

        if (fileSender != null) {
            fileSender.cancel();
        }
        fileReceiver = safeInterrupt(fileReceiver);
        safeClose(fileIn, fileConnection, fileServer);
        cancelling.set(false);
    }

    protected void createInputStream() throws IOException {
        try {
            in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
        } catch (StreamCorruptedException ex) {
            throw new IOException("version.wrong");
        }
    }

    protected void handleIOException(IOException ex) {
        if (!cancelling.get()) {
            Log.error("IO error (not cancelled)", ex);
            fireOnIOError(ex);
        } else {
            Log.info("Stopped network receiver (cancelled)");
        }
    }

    protected void fireOnIOError(IOException error) {
    }

}
