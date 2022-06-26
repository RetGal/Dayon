package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkClipboardFilesHelper;
import mpo.dayon.common.security.CustomTrustManager;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;

import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;

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

	protected SSLContext initSSLContext() throws NoSuchAlgorithmException, IOException, KeyManagementException {
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(NetworkEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());
			kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
		} catch (KeyStoreException | CertificateException | UnrecoverableKeyException e) {
			Log.error("Fatal, can not init encryption", e);
		}
		SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
		sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());
		return sslContext;
	}

}
