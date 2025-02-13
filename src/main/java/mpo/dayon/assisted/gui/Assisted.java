package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.assisted.capture.RobotCaptureFactory;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.control.RobotNetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.assisted.network.NetworkAssistedEngineListener;
import mpo.dayon.assisted.utils.ScreenUtilities;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.compressor.CompressorEngine;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.error.KeyboardErrorHandler;
import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.ClipboardDispatcher;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.Token;
import mpo.dayon.common.network.message.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.awt.event.KeyEvent.VK_CAPS_LOCK;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static mpo.dayon.assisted.network.NetworkAssistedEngine.resolveToken;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class Assisted implements Subscriber, ClipboardOwner {

    private static final String TOKEN_PARAMS = "?token=%s&rport=%d&open=%d&laddr=%s&v=1.4";

    private static final Token TOKEN = new Token(TOKEN_PARAMS);

    private String tokenServerUrl;

    private AssistedFrame frame;

    private NetworkAssistedEngineConfiguration networkConfiguration;

    private CaptureEngine captureEngine;

    private CompressorEngine compressorEngine;

    private NetworkAssistedEngine networkEngine;

    private boolean coldStart = true;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private final AtomicBoolean shareAllScreens = new AtomicBoolean(false);

    public Assisted(String tokenServerUrl) {
        networkConfiguration = new NetworkAssistedEngineConfiguration();
        updateTokenServerUrl(tokenServerUrl);

        final String lnf = getDefaultLookAndFeel();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn(format("Could not set the L&F [%s]", lnf), ex);
        }
    }

    private void updateTokenServerUrl(String tokenServerUrl) {
        if (tokenServerUrl != null && !tokenServerUrl.trim().isEmpty()) {
            this.tokenServerUrl = tokenServerUrl + TOKEN_PARAMS;
        } else if (!networkConfiguration.getTokenServerUrl().isEmpty()) {
            this.tokenServerUrl = networkConfiguration.getTokenServerUrl() + TOKEN_PARAMS;
        } else {
            this.tokenServerUrl = DEFAULT_TOKEN_SERVER_URL + TOKEN_PARAMS;
        }

        if (!this.tokenServerUrl.startsWith(DEFAULT_TOKEN_SERVER_URL)) {
            System.setProperty("dayon.custom.tokenServer", this.tokenServerUrl.substring(0, this.tokenServerUrl.indexOf('?')));
        } else {
            System.clearProperty("dayon.custom.tokenServer");
        }
    }

    /**
     * Returns true if we have a valid configuration
     */
    public boolean start(String serverName, String portNumber, boolean autoConnect) {
        // these should not block as they are called from the network incoming message thread (!)
        final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler = this::onCaptureEngineConfigured;
        final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler = this::onCompressorEngineConfigured;
        final NetworkClipboardRequestMessageHandler clipboardRequestHandler = this::onClipboardRequested;
        final NetworkScreenshotRequestMessageHandler screenshotRequestHandler = this::onScreenshotRequested;
        final NetworkControlMessageHandler controlHandler = new RobotNetworkControlMessageHandler();
        controlHandler.subscribe(this);

        networkEngine = new NetworkAssistedEngine(captureConfigurationHandler, compressorConfigurationHandler,
                controlHandler, clipboardRequestHandler, screenshotRequestHandler, this);
        networkEngine.addListener(new MyNetworkAssistedEngineListener());

        if (frame == null) {
            networkEngine.initUpnp();
            frame = new AssistedFrame(createStartAction(), createStopAction(), createToggleMultiScreenAction(), networkEngine);
            FatalErrorHandler.attachFrame(frame);
            KeyboardErrorHandler.attachFrame(frame);
            frame.setVisible(true);
            Log.info("Assisted start");
        }

        return configureConnection(serverName, portNumber, autoConnect);
    }

    private boolean configureConnection(String serverName, String portNumber, boolean autoConnect) {
        if (isValidIpAddressOrHostName(serverName) && isValidPortNumber(portNumber)) {
            networkConfiguration = new NetworkAssistedEngineConfiguration(serverName, Integer.parseInt(portNumber), autoConnect);
            Log.info("Autoconfigured " + networkConfiguration);
            networkEngine.configure(networkConfiguration);
            networkConfiguration.persist();
        } else {
            networkConfiguration = new NetworkAssistedEngineConfiguration();
            if (isValidIpAddressOrHostName(networkConfiguration.getServerName()) && isValidPortNumber(valueOf(networkConfiguration.getServerPort()))) {
                autoConnect = networkConfiguration.isAutoConnect();
                if (autoConnect) {
                    networkEngine.configure(networkConfiguration);
                }
            }
        }
        // no network settings dialog after startup
        if (coldStart) {
            coldStart = false;
            if (autoConnect) {
                networkEngine.connect(TOKEN);
            }
            return true;
        }
        return requestConnectionSettings();
    }

    private boolean requestConnectionSettings() {
        networkConfiguration = new NetworkAssistedEngineConfiguration();
        ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(networkConfiguration, TOKEN.getTokenString());

        final boolean ok = DialogFactory.showOkCancel(frame, translate("connection.settings"), connectionSettingsDialog.getTabbedPane(), false, () -> {
            final String token = connectionSettingsDialog.getToken().trim();
            if (!token.isEmpty()) {
                return isValidToken(token) ? null : translate("connection.settings.invalidToken");
            } else {
                String validationErrorMessage = validateIpAddress(connectionSettingsDialog.getIpAddress());
                if (validationErrorMessage != null) {
                    connectionSettingsDialog.getTabbedPane().setSelectedIndex(1);
                    return validationErrorMessage;
                }
                return validatePortNumber(connectionSettingsDialog.getPortNumber());
            }
        });

        if (ok) {
            applyConnectionSettings(connectionSettingsDialog);
        } else {
            // cancel
            frame.onReady();
        }
        return ok;
    }

    private static String validateIpAddress(String ipAddress) {
        if (ipAddress.isEmpty()) {
            return translate("connection.settings.emptyIpAddress");
        }
        return isValidIpAddressOrHostName(ipAddress.trim()) ? null : translate("connection.settings.invalidIpAddress");
    }

    private static String validatePortNumber(String portNumber) {
        if (portNumber.isEmpty()) {
            return translate("connection.settings.emptyPortNumber");
        }
        return isValidPortNumber(portNumber.trim()) ? null : translate("connection.settings.invalidPortNumber");
    }

    private void applyConnectionSettings(ConnectionSettingsDialog connectionSettingsDialog) {
        CompletableFuture.supplyAsync(() -> {
            final NetworkAssistedEngineConfiguration newConfiguration;
            String tokenString = connectionSettingsDialog.getToken().trim();
            if (!tokenString.isEmpty() && !tokenString.equals(TOKEN.getTokenString())) {
                TOKEN.setTokenString(tokenString);
                String connectionParams = obtainConnectionParamsFromTokenServer();
                newConfiguration = extractNetworkConfigurationFromConnectionParams(connectionParams);
            } else {
                newConfiguration = new NetworkAssistedEngineConfiguration(connectionSettingsDialog.getIpAddress().trim(),
                        Integer.parseInt(connectionSettingsDialog.getPortNumber().trim()));
            }
            return newConfiguration;
        }).thenAcceptAsync(newConfiguration -> {
            if (newConfiguration != null && !newConfiguration.getServerName().equals(networkConfiguration.getServerName())
                    || newConfiguration.getServerPort() != networkConfiguration.getServerPort()) {
                networkConfiguration = newConfiguration;
                networkConfiguration.persist();
                if (!networkConfiguration.getServerName().equals(TOKEN.getPeerAddress()) || networkConfiguration.getServerPort() != TOKEN.getPeerPort()) {
                    TOKEN.reset();
                }
                networkEngine.configure(networkConfiguration);
                frame.onConnecting(networkConfiguration.getServerName(), networkConfiguration.getServerPort());
            }
            Log.info("NetworkConfiguration " + networkConfiguration);
        });
    }

    private NetworkAssistedEngineConfiguration extractNetworkConfigurationFromConnectionParams(String connectionParams) {
        final NetworkAssistedEngineConfiguration newConfiguration;
        if (connectionParams == null || connectionParams.trim().isEmpty()) {
            // expired or wrong token server
            Log.warn("Invalid token " + TOKEN.getTokenString());
            JOptionPane.showMessageDialog(frame, translate("connection.settings.invalidToken"), translate("connection.settings.token"), JOptionPane.ERROR_MESSAGE);
            TOKEN.reset();
            stop();
            onReady();
            return null;
        }
        newConfiguration = extractConfiguration(connectionParams);
        Log.debug("Connection params " + connectionParams);
        return newConfiguration;
    }

    private String obtainConnectionParamsFromTokenServer() {
        final Cursor cursor = frame.getCursor();
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        String connectionParams = null;
        try {
            // using 0 as port and null for open as both are not known at this point
            connectionParams = resolveToken(tokenServerUrl, TOKEN.getTokenString(), 0, null);
        } catch (IOException | InterruptedException ex) {
            Log.warn("Could not resolve token " + TOKEN.getTokenString());
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            frame.setCursor(cursor);
        }
        return connectionParams;
    }

    private Action createToggleMultiScreenAction() {
        final Action multiScreen = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                initNewCaptureEngine(!shareAllScreens.get());
                shareAllScreens.set(!shareAllScreens.get());
                frame.repaint();
                if (networkEngine != null) {
                    final Dimension screenSize = ScreenUtilities.getSharedScreenSize().getSize();
                    networkEngine.sendResizeScreen(screenSize.width, screenSize.height);
                }
            }
        };
        multiScreen.putValue(Action.SHORT_DESCRIPTION, translate("share.all.screens"));
        multiScreen.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.LNF));
        return multiScreen;
    }

    private Action createStopAction() {
        final Action stopAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                stop();
            }
        };
        stopAction.setEnabled(false);
        stopAction.putValue(Action.SHORT_DESCRIPTION, translate("stop.session"));
        stopAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.STOP_LARGE));
        return stopAction;
    }

    private Action createStartAction() {
        final Action startAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                onReady();
                new NetWorker().execute();
            }
        };
        startAction.putValue(Action.SHORT_DESCRIPTION, translate("connect.assistant"));
        startAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.START_LARGE));
        return startAction;
    }

    private class NetWorker extends SwingWorker<String, String> {
        @Override
        protected String doInBackground() {
            if (isConfigured() && !isCancelled()) {
                networkEngine.configure(networkConfiguration);
                networkEngine.connect(TOKEN);
            }
            return null;
        }

        private boolean isConfigured() {
            // triggers network settings dialogue
            return start(null, null, false);
        }

        @Override
        protected void done() {
            try {
                if (!isCancelled()) {
                    super.get();
                    Log.debug(format("NetWorker is done [%s]", getNetworkConfiguration().getServerName()));
                }
            } catch (InterruptedException | ExecutionException ie) {
                Log.info("NetWorker was cancelled");
                Thread.currentThread().interrupt();
            }
        }

        private NetworkAssistedEngineConfiguration getNetworkConfiguration() {
            return networkConfiguration;
        }
    }

    private void stop() {
        Log.info("Assisted stop");
        if (networkEngine != null) {
            networkEngine.farewell();
            if (captureEngine != null) {
                captureEngine.stop();
                captureEngine = null;
            }
            if (compressorEngine != null) {
                compressorEngine.stop();
                compressorEngine = null;
            }
            networkEngine.cancel();
            networkEngine = null;
        }
        frame.onDisconnecting();
    }

    private static NetworkAssistedEngineConfiguration extractConfiguration(String connectionParams) {
        if (connectionParams != null) {
            String[] parts = connectionParams.split("\\*");
            if (parts.length > 1) {
                String assistantAddress = parts[0];
                String port = parts[1];
                // maybe extract timestamps of open and closed as well?
                if (parts.length > 7) {
                    TOKEN.updateToken(assistantAddress, Integer.parseInt(port), parts[2], parts[3].equals("0"), Integer.parseInt(parts[5]));
                } else {
                    TOKEN.updateToken(assistantAddress, Integer.parseInt(port), "",null, 0);
                }
                Log.debug(TOKEN.toString());
                return new NetworkAssistedEngineConfiguration(assistantAddress, Integer.parseInt(port));
            }
        }
        return null;
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        Log.debug("Lost clipboard ownership");
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onCaptureEngineConfigured(NetworkCaptureConfigurationMessage configuration) {
        captureEngineConfiguration = configuration.getConfiguration();

        if (captureEngine != null) {
            Log.info("Capture configuration received " + captureEngineConfiguration);
            captureEngine.reconfigure(captureEngineConfiguration);
            return;
        }

        final MouseEngine mouseEngine = new MouseEngine(networkEngine);
        mouseEngine.start();

        initNewCaptureEngine(shareAllScreens.get());
    }

    private void initNewCaptureEngine(boolean captureAllScreens) {
        if (captureEngineConfiguration == null) {
            Log.warn("CaptureEngineConfiguration is null");
            return;
        }
        if (captureEngine != null) {
            captureEngine.stop();
        }
        captureEngine = new CaptureEngine(new RobotCaptureFactory(captureAllScreens));
        captureEngine.configure(captureEngineConfiguration);
        if (compressorEngine != null) {
            captureEngine.addListener(compressorEngine);
        }
        captureEngine.start();
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onCompressorEngineConfigured(NetworkCompressorConfigurationMessage configuration) {
        final CompressorEngineConfiguration compressorEngineConfiguration = configuration.getConfiguration();

        if (compressorEngine != null) {
            Log.info("Compressor configuration received " + compressorEngineConfiguration);
            compressorEngine.reconfigure(compressorEngineConfiguration);
            return;
        }

        compressorEngine = new CompressorEngine();
        compressorEngine.configure(compressorEngineConfiguration);
        compressorEngine.addListener(networkEngine);
        compressorEngine.start(1);
        if (captureEngine != null) {
            captureEngine.addListener(compressorEngine);
        }
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onClipboardRequested() {
        Log.info("Clipboard transfer request received");
        ClipboardDispatcher.sendClipboard(networkEngine, frame, this);
    }

    private void onScreenshotRequested(){
        Log.info("Screenshot request received");
        try {
            NetworkEngine.setClipboardContents(new Robot().createScreenCapture(ScreenUtilities.getSharedScreenSize()), this);
        } catch (AWTException e) {
            Log.error("Failed to capture screen", e);
        }
    }

    @Override
    public void digest(String message) {
        KeyboardErrorHandler.warn(valueOf(message));
    }

    private void onReady() {
        frame.onReady();
    }

    private class MyNetworkAssistedEngineListener implements NetworkAssistedEngineListener {

        @Override
        public void onConnecting(String serverName, int serverPort) {
            capsOff();
            frame.onConnecting(serverName, serverPort);
        }

        @Override
        public void onPeerIsAccessible(boolean isPeerAccessible) {
            frame.onPeerIsAccessible(isPeerAccessible);
        }

        @Override
        public void onHostNotFound(String serverName) {
            stop();
            frame.onHostNotFound(serverName);
        }

        @Override
        public void onConnectionTimeout(String serverName, int serverPort) {
            stop();
            frame.onConnectionTimeout(serverName, serverPort);
        }

        @Override
        public void onRefused(String serverName, int serverPort) {
            stop();
            frame.onRefused(serverName, serverPort);
        }

        @Override
        public void onConnected(String fingerprints) {
            frame.onConnected(fingerprints);
        }

        @Override
        public void onDisconnecting() {
            frame.onDisconnecting();
        }

        @Override
        public void onIOError(IOException error) {
            stop();
            frame.onDisconnecting();
        }

        @Override
        public void onAccepting(int port) {
            frame.onAccepting(port);
        }

        @Override
        public void onReconfigured(NetworkAssistedEngineConfiguration configuration) {
            networkConfiguration = configuration;
            updateTokenServerUrl(configuration.getTokenServerUrl());
            frame.resetConnectionIndicators();
        }

        private void capsOff() {
            if (Toolkit.getDefaultToolkit().getLockingKeyState(VK_CAPS_LOCK)) {
                Log.info("Caps Lock is on, turning it off");
                try {
                    Toolkit.getDefaultToolkit().setLockingKeyState(VK_CAPS_LOCK, false);
                } catch (UnsupportedOperationException e) {
                    final Robot robot;
                    try {
                        robot = new Robot();
                    } catch (AWTException ex) {
                        throw new IllegalStateException("Could not initialize the AWT robot!", ex);
                    }
                    robot.keyPress(VK_CAPS_LOCK);
                    robot.delay(10);
                    robot.keyRelease(VK_CAPS_LOCK);
                }
            }
        }
    }
}
