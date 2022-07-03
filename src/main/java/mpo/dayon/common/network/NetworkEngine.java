package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkClipboardFilesHelper;
import mpo.dayon.common.network.message.NetworkClipboardFilesMessage;
import mpo.dayon.common.network.message.NetworkMessage;
import mpo.dayon.common.network.message.NetworkMessageType;
import mpo.dayon.common.security.CustomTrustManager;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;

import static java.lang.String.format;
import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;
import static mpo.dayon.common.utils.SystemUtilities.getTempDir;

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

	private NetworkClipboardFilesHelper handleNetworkClipboardFilesHelper(NetworkClipboardFilesHelper filesHelper, ClipboardOwner clipboardOwner) {
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
			} else {
				type = CLIPBOARD_FILES;
			}

			if (type.equals(CLIPBOARD_FILES)) {
				filesHelper = handleNetworkClipboardFilesHelper(NetworkClipboardFilesMessage.unmarshall(fileIn,
						filesHelper, tmpDir), clipboardOwner);
				if (filesHelper.isDone()) {
					fireOnClipboardReceived();
				}
			} else if (!type.equals(PING)) {
				throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
			}
		}
	}

	protected void fireOnClipboardReceived() {
	}

}
