package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.assisted.capture.RobotCaptureFactory;
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
import java.security.NoSuchAlgorithmException;
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

    private static final String TOKEN_PARAMS = "?token=%s&rport=%d&open=%d&laddr=%s&v=1.5";

    private static final Token TOKEN = new Token(TOKEN_PARAMS);

    public static final String INVALID_TOKEN = "connection.settings.invalidToken";

    private static boolean isWayland = false;

    private String tokenServerUrl;

    private AssistedFrame frame;

    private NetworkAssistedEngineConfiguration networkConfiguration;

    private CaptureEngine captureEngine;

    private CompressorEngine compressorEngine;

    private MouseEngine mouseEngine;

    private NetworkAssistedEngine networkEngine;

    private boolean coldStart = true;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private final AtomicBoolean shareAllScreens = new AtomicBoolean(false);

    private final String tokenServerUrlFromYaml;

    public Assisted(String tokenServerUrl) {
        tokenServerUrlFromYaml = tokenServerUrl;
        networkConfiguration = new NetworkAssistedEngineConfiguration();
        updateTokenServerUrl(tokenServerUrl);
        initLookAndFeel();
        detectDesktopSession();
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(getDefaultLookAndFeel());
        } catch (Exception ex) {
            Log.warn(format("Could not set the L&F [%s]", getDefaultLookAndFeel()), ex);
        }
    }

    private static void detectDesktopSession() {
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        if (sessionType != null && sessionType.equals("wayland")) {
            Log.warn("Wayland session detected");
            isWayland = true;
            System.setProperty("xdg.session.type", sessionType);
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
        updateCustomTokenServerProperty();
    }

    private void updateCustomTokenServerProperty() {
        if (!tokenServerUrl.startsWith(DEFAULT_TOKEN_SERVER_URL)) {
            System.setProperty("dayon.custom.tokenServer", tokenServerUrl.substring(0, tokenServerUrl.indexOf('?')));
        } else {
            System.clearProperty("dayon.custom.tokenServer");
        }
    }

    /**
     * Returns true if we have a valid configuration
     */
    public boolean start(String serverName, String portNumber, boolean autoConnect) {
        networkEngine = createNetworkEngine();
        networkEngine.addListener(new MyNetworkAssistedEngineListener());
        if (frame == null) {
            initializeFrame();
        }
        return configureConnection(serverName, portNumber, autoConnect);
    }

    private NetworkAssistedEngine createNetworkEngine() {
        return new NetworkAssistedEngine(
                this::onCaptureEngineConfigured,
                this::onCompressorEngineConfigured,
                new RobotNetworkControlMessageHandler(),
                this::onClipboardRequested,
                this::onScreenshotRequested,
                this
        );
    }

    private void initializeFrame() {
        networkEngine.initUpnp();
        frame = new AssistedFrame(
                createStartAction(), createStopAction(), createToggleMultiScreenAction(),
                networkEngine, tokenServerUrlFromYaml != null && !tokenServerUrlFromYaml.isEmpty(), isWayland
        );
        FatalErrorHandler.attachFrame(frame);
        KeyboardErrorHandler.attachFrame(frame);
        frame.setVisible(true);
        Log.info("Assisted start");
    }

    private boolean configureConnection(String serverName, String portNumber, boolean autoConnect) {
        if (isValidIpAddressOrHostName(serverName) && isValidPortNumber(portNumber)) {
            networkConfiguration = new NetworkAssistedEngineConfiguration(serverName, Integer.parseInt(portNumber), autoConnect);
            Log.info("Autoconfigured " + networkConfiguration);
            networkEngine.configure(networkConfiguration);
            networkConfiguration.persist();
        } else if (isValidIpAddressOrHostName(networkConfiguration.getServerName()) && isValidPortNumber(valueOf(networkConfiguration.getServerPort()))) {
            autoConnect = networkConfiguration.isAutoConnect();
            if (autoConnect) {
                networkEngine.configure(networkConfiguration);
            }
        }
        // no network settings dialog after startup
        if (coldStart) {
            coldStart = false;
            if (autoConnect) {
                // 2 minutes should be plenty of time
                networkEngine.connect(TOKEN, 120000);
            }
            return true;
        }
        return requestConnectionSettings();
    }

    private boolean requestConnectionSettings() {
        ConnectionSettingsDialog dialog = new ConnectionSettingsDialog(networkConfiguration, TOKEN.getTokenString());
        boolean ok = DialogFactory.showOkCancel(frame, translate("connection.settings"), dialog.getTabbedPane(), false, true,
                () -> validateConnectionSettings(dialog));

        if (ok) {
            applyConnectionSettings(dialog);
        } else {
            // cancel
            frame.onReady();
        }
        return ok;
    }

    private String validateConnectionSettings(ConnectionSettingsDialog dialog) {
        String token = dialog.getToken().trim();
        if (!token.isEmpty() && !token.equals(TOKEN.getTokenString())) {
            try {
                return isValidToken(token) ? null : translate(INVALID_TOKEN);
            } catch (NoSuchAlgorithmException ex) {
                return translate(INVALID_TOKEN);
            }
        }

        String ipValidation = validateIpAddress(dialog.getIpAddress());
        if (ipValidation != null) {
            dialog.getTabbedPane().setSelectedIndex(1);
            return ipValidation;
        }

        return validatePortNumber(dialog.getPortNumber());
    }

    private static String validateIpAddress(String ipAddress) {
        if (ipAddress.isEmpty()) {
            return translate("connection.settings.emptyIpAddress");
        }
        if (!isValidIpAddressOrHostName(ipAddress.trim())) {
            return translate("connection.settings.invalidIpAddress");
        }
        return null;
    }

    private static String validatePortNumber(String portNumber) {
        if (portNumber.isEmpty()) {
            return translate("connection.settings.emptyPortNumber");
        }
        if (!isValidPortNumber(portNumber.trim())) {
            return translate("connection.settings.invalidPortNumber");
        }
        return null;
    }

    private void applyConnectionSettings(ConnectionSettingsDialog dialog) {
        CompletableFuture.supplyAsync(() -> {
            String tokenString = dialog.getToken().trim();
            if (!tokenString.isEmpty() && !tokenString.equals(TOKEN.getTokenString())) {
                return processTokenConfiguration(tokenString);
            } else {
                return new NetworkAssistedEngineConfiguration(
                        dialog.getIpAddress().trim(),
                        Integer.parseInt(dialog.getPortNumber().trim())
                );
            }
        }).thenAcceptAsync(this::applyNewConfiguration);
    }

    private NetworkAssistedEngineConfiguration processTokenConfiguration(String tokenString) {
        Log.debug("Applying new token: " + tokenString);
        TOKEN.setTokenString(tokenString);
        frame.disableStartButton();
        String connectionParams = obtainConnectionParamsFromTokenServer();
        return extractNetworkConfigurationFromConnectionParams(connectionParams);
    }

    private void applyNewConfiguration(NetworkAssistedEngineConfiguration newConfig) {
        if (newConfig != null && !newConfig.getServerName().equals(networkConfiguration.getServerName())
                || newConfig.getServerPort() != networkConfiguration.getServerPort()) {
            Log.debug("Applying new configuration: " + newConfig);
            networkConfiguration = newConfig;
            networkConfiguration.persist();
            if (!networkConfiguration.getServerName().equals(TOKEN.getPeerAddress()) || networkConfiguration.getServerPort() != TOKEN.getPeerPort()) {
                TOKEN.reset();
            }
            networkEngine.configure(networkConfiguration);
            frame.onConnecting(networkConfiguration.getServerName(), networkConfiguration.getServerPort());
        }
        frame.enableStartButton();
    }

    private NetworkAssistedEngineConfiguration extractNetworkConfigurationFromConnectionParams(String connectionParams) {
        if (connectionParams == null || connectionParams.trim().isEmpty()) {
            // expired or wrong token server
            Log.warn("Invalid token " + TOKEN.getTokenString());
            JOptionPane.showMessageDialog(frame, translate(INVALID_TOKEN),
                    translate("connection.settings.token"), JOptionPane.ERROR_MESSAGE);
            TOKEN.reset();
            stop();
            onReady();
            return null;
        }
        return extractConfiguration(connectionParams);
    }

    private String obtainConnectionParamsFromTokenServer() {
        Cursor cursor = frame.getCursor();
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        String connectionParams = null;
        try {
            // using 0 as port and null for open as both are not known at this point
            connectionParams = resolveToken(tokenServerUrl, TOKEN.getTokenString(), 0, null, networkEngine.getLocalAddress());
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
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                shareAllScreens.set(!shareAllScreens.get());
                initNewCaptureEngine(shareAllScreens.get());
                frame.repaint();
                if (networkEngine != null) {
                    Dimension screenSize = ScreenUtilities.getSharedScreenSize().getSize();
                    networkEngine.sendResizeScreen(screenSize.width, screenSize.height);
                }
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, translate("share.all.screens"));
        action.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.LNF));
        return action;
    }

    private Action createStopAction() {
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                stop();
            }
        };
        action.setEnabled(false);
        action.putValue(Action.SHORT_DESCRIPTION, translate("stop.session"));
        action.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.STOP_LARGE));
        return action;
    }

    private Action createStartAction() {
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                onReady();
                new NetWorker().execute();
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, translate("connect.assistant"));
        action.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.START_LARGE));
        return action;
    }

    private void stop() {
        Log.info("Assisted stop");
        if (networkEngine != null) {
            networkEngine.farewell();
            stopCaCoMoEngines();
            networkEngine.cancel();
            networkEngine = null;
        }
        frame.onDisconnecting();
    }

    private void stopCaCoMoEngines() {
        if (captureEngine != null) {
            captureEngine.stop();
            captureEngine = null;
        }
        if (compressorEngine != null) {
            compressorEngine.stop();
            compressorEngine = null;
        }
        if (mouseEngine != null) {
            mouseEngine.stop();
            mouseEngine = null;
        }
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onCaptureEngineConfigured(NetworkCaptureConfigurationMessage config) {
        captureEngineConfiguration = config.getConfiguration();
        if (captureEngine != null) {
            Log.info("Capture configuration received " + captureEngineConfiguration);
            captureEngine.reconfigure(captureEngineConfiguration);
            return;
        }
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
    private void onCompressorEngineConfigured(NetworkCompressorConfigurationMessage config) {
        CompressorEngineConfiguration compConfig = config.getConfiguration();
        if (compressorEngine != null) {
            Log.info("Compressor configuration received " + compConfig);
            compressorEngine.reconfigure(compConfig);
            return;
        }
        compressorEngine = new CompressorEngine();
        compressorEngine.configure(compConfig);
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

    private void onScreenshotRequested() {
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

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        Log.debug("Lost clipboard ownership");
    }

    private void onReady() {
        frame.onReady();
    }

    private static NetworkAssistedEngineConfiguration extractConfiguration(String connectionParams) {
        String[] parts = connectionParams.split("\\*");
        if (parts.length <= 1) return null;

        String assistantAddress = parts[0];
        int port = Integer.parseInt(parts[1]);
        // maybe extract timestamps of open and closed as well?
        if (parts.length > 4) {
            // 0 assistant 1 port 2 assistant_local 3 closed 4 rport 5 $assistant_ice
            TOKEN.updateToken(assistantAddress, port, parts[2], parts[3].equals("0"), Integer.parseInt(parts[4]), parts[5]);
        } else {
            TOKEN.updateToken(assistantAddress, port, "", null, 0, null);
        }
        Log.debug(TOKEN.toString());
        return new NetworkAssistedEngineConfiguration(assistantAddress, port);
    }

    private class NetWorker extends SwingWorker<String, String> {
        @Override
        protected String doInBackground() {
            if (isConfigured() && !isCancelled()) {
                networkEngine.configure(networkConfiguration);
                networkEngine.connect(TOKEN, 7000);
            }
            return null;
        }

        private boolean isConfigured() {
            return start(null, null, false);
        }

        @Override
        protected void done() {
            if (!isCancelled()) {
                try {
                    get();
                    Log.debug(format("NetWorker is done [%s]", networkConfiguration.getServerName()));
                } catch (InterruptedException | ExecutionException ex) {
                    Log.info("NetWorker was cancelled");
                    Log.error(ex.getCause().getMessage(), ex);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private class MyNetworkAssistedEngineListener implements NetworkAssistedEngineListener {
        @Override
        public void onConnecting(String serverName, int serverPort) {
            capsOff();
            frame.onConnecting(serverName, serverPort);
        }

        @Override
        public void onIceConnecting() {
            frame.disableStopButton();
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
        public void onConnected(String fingerprints, boolean isRevertedConnection) {
            frame.onConnected(fingerprints, isRevertedConnection);
            // reset the capture engine in order to transmit a full capture, important in case of reconnects
            if (captureEngine != null) {
                captureEngine.reconfigure(captureEngineConfiguration);
            } else {
                initNewCaptureEngine(shareAllScreens.get());
            }
            if (mouseEngine == null) {
                mouseEngine = new MouseEngine(networkEngine);
            }
            mouseEngine.start();
        }

        @Override
        public void onDisconnecting() {
            stopCaCoMoEngines();
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
            if (!networkConfiguration.getServerName().equals(configuration.getServerName())
                    || networkConfiguration.getServerPort() != configuration.getServerPort()) {
                TOKEN.reset();
            }
            networkConfiguration = configuration;
            updateTokenServerUrl(configuration.getTokenServerUrl());
            frame.resetConnectionIndicators();
        }

        private void capsOff() {
            if (!Toolkit.getDefaultToolkit().getLockingKeyState(VK_CAPS_LOCK)) {
                return;
            }
            Log.info("Caps Lock is on, turning it off");
            try {
                Toolkit.getDefaultToolkit().setLockingKeyState(VK_CAPS_LOCK, false);
            } catch (UnsupportedOperationException e) {
                try {
                    Robot robot = new Robot();
                    robot.keyPress(VK_CAPS_LOCK);
                    robot.delay(10);
                    robot.keyRelease(VK_CAPS_LOCK);
                } catch (AWTException ex) {
                    throw new IllegalStateException("Could not initialize the AWT robot!", ex);
                }
            }
        }
    }
}