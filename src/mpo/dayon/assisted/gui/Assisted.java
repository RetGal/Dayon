package mpo.dayon.assisted.gui;

import java.awt.GridLayout;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.capture.RobotCaptureFactory;
import mpo.dayon.assisted.compressor.CompressorEngine;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.control.RobotNetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngine;
import mpo.dayon.assisted.mouse.MouseEngineConfiguration;
import mpo.dayon.assisted.network.NetworkAssistedEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.error.SeriousErrorHandler;
import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkCaptureConfigurationMessage;
import mpo.dayon.common.network.message.NetworkCaptureConfigurationMessageHandler;
import mpo.dayon.common.network.message.NetworkCompressorConfigurationMessage;
import mpo.dayon.common.network.message.NetworkCompressorConfigurationMessageHandler;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.utils.SystemUtilities;

public class Assisted implements Subscriber {
	private AssistedFrame frame;

	private NetworkAssistedEngineConfiguration configuration;

	private volatile CaptureEngine captureEngine;

	private volatile CompressorEngine compressorEngine;

	public Assisted() {
	}

	public void configure() {
		final String lnf = SystemUtilities.getDefaultLookAndFeel();
		try {
			UIManager.setLookAndFeel(lnf);
		} catch (Exception ex) {
			Log.warn("Cound not set the [" + lnf + "] L&F!", ex);
		}
	}

	public void start() throws IOException {
		frame = new AssistedFrame(new AssistedFrameConfiguration());

		FatalErrorHandler.attachFrame(frame);
		SeriousErrorHandler.attachFrame(frame);

		frame.setVisible(true);

		// accept own cert, avoid PKIX path building exception
		SSLContext sc = null;
		try {
			sc = SSLContext.getInstance("TLS");
			sc.init(null, new TrustManager[] { new CustomTrustManager() }, null);
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			Log.error(e.getMessage());
			System.exit(1);
		}
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// accept own cert, avoid No name matching host found exception
		HttpsURLConnection.setDefaultHostnameVerifier(new HostNameIgnorer()); //

		configuration = new NetworkAssistedEngineConfiguration();

		final String ip = SystemUtilities.getStringProperty(null, "dayon.assistant.ipAddress", null);
		final Integer port = SystemUtilities.getIntProperty(null, "dayon.assistant.portNumber", -1);

		if (ip == null) {
			if (!requestConnectionSettings()) {
				Log.info("Bye!");
				System.exit(0);
			}
		} else // JNLP startup (!)
		{
			final NetworkAssistedEngineConfiguration xconfiguration = new NetworkAssistedEngineConfiguration(ip, port);
			if (!xconfiguration.equals(configuration)) {
				configuration = xconfiguration;
				configuration.persist();
			}
		}

		Log.info("Configuration " + configuration);

		// -------------------------------------------------------------------------------------------------------------
		// HTTP request to notify the assistant we're ready and starting : i.e.,
		// JNLP stuff is done.

		frame.onHttpConnecting(configuration);

		Log.info("[HTTPS-handshake] Sending the /hello request...");

		try {
			final URL url = new URL("https://" + configuration.getServerName() + ":" + configuration.getServerPort() + "/hello");
			final URLConnection conn = url.openConnection();

			conn.getInputStream();

			// Currently do not care about the response : returning a 404
			// response (!)
			throw new RuntimeException("[HTTPS-handshake] Missing expected /hello response!");
		} catch (FileNotFoundException expected) {
			// ignored
		}

		Log.info("[HTTPS-handshake] Expected response (404) received.");

		// -------------------------------------------------------------------------------------------------------------

		final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler = new NetworkCaptureConfigurationMessageHandler() {
			/**
			 * Should not block as called from the network incoming message
			 * thread (!)
			 */
			public void handleConfiguration(NetworkEngine engine, NetworkCaptureConfigurationMessage configuration) {
				onCaptureEngineConfigured(engine, configuration);
				frame.onConnected();
			}
		};

		final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler = new NetworkCompressorConfigurationMessageHandler() {
			/**
			 * Should not block as called from the network incoming message
			 * thread (!)
			 */
			public void handleConfiguration(NetworkEngine engine, NetworkCompressorConfigurationMessage configuration) {
				onCompressorEngineConfigured(engine, configuration);
				frame.onConnected();
			}
		};

		final NetworkControlMessageHandler controlHandler = new RobotNetworkControlMessageHandler();
		
		controlHandler.subscribe(this);

		frame.onConnecting(configuration);

		final NetworkAssistedEngine networkEngine = new NetworkAssistedEngine(captureConfigurationHandler, compressorConfigurationHandler, controlHandler);

		networkEngine.configure(configuration);
		try {
			networkEngine.start();
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException | KeyManagementException | UnrecoverableKeyException e) {
			Log.error(e.getMessage());
			System.exit(1);
		}
		networkEngine.sendHello();
	}

	private boolean requestConnectionSettings() {
		final JPanel pane = new JPanel();

		pane.setLayout(new GridLayout(2, 2, 10, 10));

		final JLabel assistantIpAddress = new JLabel(Babylon.translate("connection.settings.assistantIpAddress"));
		final JTextField assistantIpAddressTextField = new JTextField();
		assistantIpAddressTextField.setText(configuration.getServerName());

		pane.add(assistantIpAddress);
		pane.add(assistantIpAddressTextField);

		final JLabel assistantPortNumberLbl = new JLabel(Babylon.translate("connection.settings.assistantPortNumber"));
		final JTextField assistantPortNumberTextField = new JTextField();
		assistantPortNumberTextField.setText(String.valueOf(configuration.getServerPort()));

		pane.add(assistantPortNumberLbl);
		pane.add(assistantPortNumberTextField);

		final boolean ok = DialogFactory.showOkCancel(frame, Babylon.translate("connection.settings"), pane, new DialogFactory.Validator() {
			public String validate() {
				final String ipAddress = assistantIpAddressTextField.getText();
				if (ipAddress.isEmpty()) {
					return Babylon.translate("connection.settings.emptyIpAddress");
				}

				final String portNumber = assistantPortNumberTextField.getText();
				if (portNumber.isEmpty()) {
					return Babylon.translate("connection.settings.emptyPortNumber");
				}

				try {
					Integer.valueOf(portNumber);
				} catch (NumberFormatException ex) {
					return Babylon.translate("connection.settings.invalidPortNumber");
				}

				return null;
			}
		});

		if (ok) {
			final NetworkAssistedEngineConfiguration xconfiguration = new NetworkAssistedEngineConfiguration(assistantIpAddressTextField.getText(),
					Integer.valueOf(assistantPortNumberTextField.getText()));
			if (!xconfiguration.equals(configuration)) {
				configuration = xconfiguration;
				configuration.persist();
			}
		}

		return ok;
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	private void onCaptureEngineConfigured(NetworkEngine engine, NetworkCaptureConfigurationMessage configuration) {
		final CaptureEngineConfiguration captureEngineConfiguration = configuration.getConfiguration();

		Log.info("Capture configuration received " + captureEngineConfiguration);

		if (captureEngine != null) {
			captureEngine.reconfigure(captureEngineConfiguration);
			return;
		}

		// First time we receive a configuration from the assistant (!)

		// Setup the mouse engine (no need before I guess)

		final MouseEngine mouseEngine = new MouseEngine();

		mouseEngine.configure(new MouseEngineConfiguration());
		mouseEngine.addListener((NetworkAssistedEngine) engine);
		mouseEngine.start();

		captureEngine = new CaptureEngine(new RobotCaptureFactory());
		captureEngine.configure(captureEngineConfiguration);

		if (compressorEngine != null) {
			captureEngine.addListener(compressorEngine);
		}

		captureEngine.start();
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	private void onCompressorEngineConfigured(NetworkEngine engine, NetworkCompressorConfigurationMessage configuration) {
		final CompressorEngineConfiguration compressorEngineConfiguration = configuration.getConfiguration();

		Log.info("Compressor configuration received " + compressorEngineConfiguration);

		if (compressorEngine != null) {
			compressorEngine.reconfigure(compressorEngineConfiguration);
			return;
		}

		compressorEngine = new CompressorEngine();

		compressorEngine.configure(compressorEngineConfiguration);
		compressorEngine.addListener((NetworkAssistedEngine) engine);
		compressorEngine.start(1);

		if (captureEngine != null) {
			captureEngine.addListener(compressorEngine);
		}
	}

	private class HostNameIgnorer implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}

	}

	@Override
	public void digest(String message) {
		SeriousErrorHandler.warn(String.valueOf(message));
	}

}
