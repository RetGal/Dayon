package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkClipboardFilesHelper;
import mpo.dayon.common.utils.TransferableFiles;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;

/**
 * Both the assistant and the assisted are talking to each other using a very
 * simple asynchronous network message layer. The network engine is handling
 * both the sending and the receiving sides.
 */
public abstract class NetworkEngine {

	protected static final String UNSUPPORTED_TYPE = "Unsupported message type [%s]!";

	protected void setClipboardContents(String string, ClipboardOwner clipboardOwner) {
		Log.debug("setClipboardContents " + string);
		StringSelection stringSelection = new StringSelection(string);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, clipboardOwner);
	}

	private void setClipboardContents(List<File> files, ClipboardOwner clipboardOwner) {
		Log.debug("setClipboardContents " + files.toString());
		TransferableFiles transferableFiles = new TransferableFiles(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferableFiles, clipboardOwner);
	}

	protected NetworkClipboardFilesHelper handleNetworkClipboardFilesHelper(NetworkClipboardFilesHelper filesHelper, ClipboardOwner clipboardOwner) {
		if (filesHelper.isDone()) {
			setClipboardContents(filesHelper.getFiles(), clipboardOwner);
			return new NetworkClipboardFilesHelper();
		}
		return filesHelper;
	}

}
