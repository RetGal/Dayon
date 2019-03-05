package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkClipboardFilesHelper;
import mpo.dayon.common.network.message.NetworkClipboardFilesMessage;
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

	protected NetworkClipboardFilesHelper handleNetworkClipboardFilesHelper(NetworkClipboardFilesHelper filesHelper, NetworkClipboardFilesMessage clipboardFiles, ClipboardOwner clipboardOwner) {
		filesHelper.setTotalFileBytesLeft(clipboardFiles.getWireSize() - 1L);
		if (filesHelper.isIdle()) {
			filesHelper = new NetworkClipboardFilesHelper();
			setClipboardContents(clipboardFiles.getFiles(), clipboardOwner);
		} else {
			filesHelper.setFiles(clipboardFiles.getFiles());
			filesHelper.setFileNames(clipboardFiles.getFileNames());
			filesHelper.setFileSizes(clipboardFiles.getFileSizes());
			filesHelper.setPosition(clipboardFiles.getPosition());
			filesHelper.setFileBytesLeft(clipboardFiles.getRemainingFileSize());
		}
		return filesHelper;
	}

}
