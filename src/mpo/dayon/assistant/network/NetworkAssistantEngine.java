package mpo.dayon.assistant.network;

import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import mpo.dayon.assistant.network.https.NetworkAssistantHttpsEngine;
import mpo.dayon.assistant.network.https.NetworkAssistantHttpsResources;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.NetworkCaptureMessage;
import mpo.dayon.common.network.message.NetworkCaptureMessageHandler;
import mpo.dayon.common.network.message.NetworkHelloMessage;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMessage;
import mpo.dayon.common.network.message.NetworkMessageType;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import mpo.dayon.common.network.message.NetworkMouseLocationMessage;
import mpo.dayon.common.network.message.NetworkMouseLocationMessageHandler;
import mpo.dayon.common.utils.SystemUtilities;
import mpo.dayon.common.version.Version;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantConfiguration> {
	
	private final NetworkCaptureMessageHandler captureMessageHandler;

	private final NetworkMouseLocationMessageHandler mouseMessageHandler;

	private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>(NetworkAssistantEngineListener.class);

	private NetworkAssistantConfiguration configuration;

	/**
	 * IN.
	 */
	private Thread receiver;

	/**
	 * OUT.
	 */
	private NetworkSender sender;

	private SSLServerSocket server;

	private Socket connection;

	private final AtomicBoolean cancelling = new AtomicBoolean(false);

	private NetworkAssistantHttpsEngine https;

	/**
	 * I've to cleanup that stuff ASAP (!)
	 */
	public String __ipAddress = "127.0.0.1";

	public NetworkAssistantEngine(NetworkCaptureMessageHandler captureMessageHandler, NetworkMouseLocationMessageHandler mouseMessageHandler) {
		this.captureMessageHandler = captureMessageHandler;
		this.mouseMessageHandler = mouseMessageHandler;

		fireOnReady();
	}

	public void configure(NetworkAssistantConfiguration configuration) {
		this.configuration = configuration;
	}

	public void reconfigure(NetworkAssistantConfiguration configuration) {
		this.configuration = configuration;
	}

	public void addListener(NetworkAssistantEngineListener listener) {
		listeners.add(listener);
	}

	public void removeListener(NetworkAssistantEngineListener listener) {
		listeners.remove(listener);
	}

	public int getPort() {
		return configuration.getPort();
	}

	/**
	 * Possibly called from a GUI action => do not block the AWT thread (!)
	 */
	public void start() {
		if (cancelling.get() || receiver != null) {
			return;
		}

		receiver = new Thread(new RunnableEx() {
			protected void doRun() throws Exception {
				NetworkAssistantEngine.this.receivingLoop();
			}
		}, "NetworkReceiver");

		receiver.start();
	}

	/**
	 * Possibly called from a GUI action => do not block the AWT thread (!)
	 */
	public void cancel() {
		Log.info("Cancelling the network assistant engine...");

		cancelling.set(true);

		if (https != null) {
			https.cancel();
			https = null;
		}

		SystemUtilities.safeClose(server);
		SystemUtilities.safeClose(connection);
	}

	private void receivingLoop() throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
		DataInputStream in = null;
		DataOutputStream out = null;

		try {
			final int port = getPort();

			Log.info(String.format("HTTP server [port:%d]", port));
			fireOnHttpStarting(port);

			NetworkAssistantHttpsResources.setup(__ipAddress, port); // JNLP support (.html, .jnlp, .jar)

			https = new NetworkAssistantHttpsEngine(port);
			https.start(); // blocking call until the HTTP-acceptor has been closed (!)

			Log.info(String.format("Dayon! server [port:%d]", configuration.getPort()));
			fireOnStarting(configuration.getPort());

			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(NetworkAssistantHttpsEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), null, null);
			
			SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
			server = (SSLServerSocket) ssf.createServerSocket(port);

			Log.info("Accepting ...");
			fireOnAccepting(port);

			if (https != null) {
				https.onDayonAccepting();
			}

			do {
				SystemUtilities.safeClose(connection); // we might have refused the accepted connection (!)

				connection = server.accept();

				Log.info(String.format("Incoming connection from [%s]", connection));
			} while (!fireOnAccepted(connection));

			server.close();
			server = null;

			in = new DataInputStream(new BufferedInputStream(connection.getInputStream()));
			out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));

			sender = new NetworkSender(out);
			sender.start(8);

			boolean introduced = false;

			while (true) {
				NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)

				final NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);

				switch (type) {
				case HELLO:
					if (introduced) {
						throw new IOException("Unexpected message [HELLO]!");
					}

					if (https != null) {
						https.cancel();
						https = null;
					}

					final NetworkHelloMessage hello = NetworkHelloMessage.unmarshall(in);
					fireOnByteReceived(1 + hello.getWireSize()); // +1 : magic number (byte)

					final Version version = Version.get();
					final boolean isProd = isProd(version, hello.getMajor(), hello.getMinor());

					if (isProd && (version.getMajor() != hello.getMajor() || version.getMinor() != hello.getMinor())) {
						throw new IOException("Version Error!");
					}

					introduced = true;
					fireOnConnected(connection);

					break;

				case CAPTURE:
					if (!introduced) {
						throw new IOException("Unexpected message [CAPTURE]!");
					}

					final NetworkCaptureMessage capture = NetworkCaptureMessage.unmarshall(in);
					fireOnByteReceived(1 + capture.getWireSize()); // +1 : magic number (byte)

					captureMessageHandler.handleCapture(capture);

					break;

				case MOUSE_LOCATION:
					if (!introduced) {
						throw new IOException("Unexpected message [CAPTURE]!");
					}

					final NetworkMouseLocationMessage mouse = NetworkMouseLocationMessage.unmarshall(in);
					fireOnByteReceived(1 + mouse.getWireSize()); // +1 : magic number (byte)

					mouseMessageHandler.handleLocation(mouse);

					break;

				default:
					throw new IOException("Unsupported message type [" + type + "]!");
				}
			}
		} catch (IOException ex) {
			Log.error("IO error (cancelling:" + cancelling + ")!", ex);

			if (!cancelling.get()) {
				fireOnIOError(ex);
			}

			if (sender != null) {
				sender.cancel();
			}

			SystemUtilities.safeClose(in);
			SystemUtilities.safeClose(out);

			cancelling.set(false);
			server = null;
			connection = null;
			receiver = null;
		} catch (KeyManagementException | UnrecoverableKeyException e) {
			Log.error("Fatal, can not init encryption", e);
		}

		fireOnReady();
	}

	private static boolean isProd(Version version, int major, int minor) {
		return !(version.isNull() || (major == 0 && minor == 0));
	}

	/**
	 * Might be blocking if the sender queue is full (!)
	 */
	public void sendCaptureConfiguration(CaptureEngineConfiguration configuration) {
		if (sender != null) {
			sender.sendCaptureConfiguration(configuration);
		}
	}

	/**
	 * Might be blocking if the sender queue is full (!)
	 */
	public void sendCompressorConfiguration(CompressorEngineConfiguration configuration) {
		if (sender != null) {
			sender.sendCompressorConfiguration(configuration);
		}
	}

	/**
	 * Might be blocking if the sender queue is full (!)
	 */
	public void sendMouseControl(NetworkMouseControlMessage message) {
		if (sender != null) {
			sender.sendMouseControl(message);
		}
	}

	/**
	 * Might be blocking if the sender queue is full (!)
	 */
	public void sendKeyControl(NetworkKeyControlMessage message) {
		if (sender != null) {
			sender.sendKeyControl(message);
		}
	}

	private void fireOnReady() {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onReady();
		}
	}

	private void fireOnHttpStarting(int port) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onHttpStarting(port);
		}
	}

	private void fireOnStarting(int port) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onStarting(port);
		}
	}

	private void fireOnAccepting(int port) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onAccepting(port);
		}
	}

	private boolean fireOnAccepted(Socket connection) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return true;
		}

		boolean ok = true;

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			if (!xlistener.onAccepted(connection)) {
				ok = false;
			}
		}

		return ok;
	}

	private void fireOnConnected(Socket connection) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onConnected(connection);
		}
	}

	private void fireOnByteReceived(int count) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onByteReceived(count);
		}
	}

	private void fireOnIOError(IOException error) {
		final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

		if (xlisteners == null) {
			return;
		}

		for (final NetworkAssistantEngineListener xlistener : xlisteners) {
			xlistener.onIOError(error);
		}
	}
}
