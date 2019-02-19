package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.TransferableFiles;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Both the assistant and the assisted are talking to each other using a very
 * simple asynchronous network message layer. The network engine is handling
 * both the sending and the receiving sides.
 */
public abstract class NetworkEngine {
	public abstract void start() throws IOException, NoSuchAlgorithmException, KeyManagementException;

	public void setClipboardContents(String string, ClipboardOwner clipboardOwner) {
		Log.debug("setClipboardContents " + string);
		StringSelection stringSelection = new StringSelection(string);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, clipboardOwner);
	}

	public void setClipboardContents(List<File> files, ClipboardOwner clipboardOwner) {
		Log.debug("setClipboardContents " + files.toString());
		TransferableFiles transferableFiles = new TransferableFiles(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferableFiles, clipboardOwner);
	}

}
