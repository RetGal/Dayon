package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.capture.RobotCaptureFactory;
import mpo.dayon.assisted.compressor.CompressorEngine;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.control.RobotNetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.assisted.network.NetworkAssistedEngineListener;
import mpo.dayon.assisted.utils.ScreenUtilities;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.error.KeyboardErrorHandler;
import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.TransferableImage;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.network.FileUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class Assisted implements Subscriber, ClipboardOwner {

    private static final String TOKEN_PARAM = "?token=%s";

    private final String tokenServerUrl;

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

        if (tokenServerUrl != null) {
            this.tokenServerUrl = tokenServerUrl + TOKEN_PARAM;
            System.setProperty("dayon.custom.tokenServer", tokenServerUrl);
        } else if (!networkConfiguration.getTokenServerUrl().isEmpty()) {
            this.tokenServerUrl = networkConfiguration.getTokenServerUrl() + TOKEN_PARAM;
            System.setProperty("dayon.custom.tokenServer", this.tokenServerUrl);
        } else {
            this.tokenServerUrl = DEFAULT_TOKEN_SERVER_URL + TOKEN_PARAM;
        }

        final String lnf = getDefaultLookAndFeel();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn(format("Could not set the L&F [%s]", lnf), ex);
        }
    }

    /**
     * Returns true if we have a valid configuration
     */
    public boolean start(String serverName, String portNumber, boolean autoConnect) {
        Log.info("Assisted start");

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
            frame = new AssistedFrame(createStartAction(), createStopAction(), createConnectionSettingsAction(), createToggleMultiScreenAction());
            FatalErrorHandler.attachFrame(frame);
            KeyboardErrorHandler.attachFrame(frame);
            frame.setVisible(true);
        }
        return configureConnection(serverName, portNumber, autoConnect);
    }

    private NetworkAssistedEngineConfiguration getNetworkConfiguration() {
        return networkConfiguration;
    }

    private boolean configureConnection(String serverName, String portNumber, boolean autoConnect) {
        if (isValidIpAddressOrHostName(serverName) && isValidPortNumber(portNumber)) {
            networkConfiguration = new NetworkAssistedEngineConfiguration(serverName, Integer.parseInt(portNumber), autoConnect);
            Log.info("Autoconfigured " + networkConfiguration);
            networkEngine.configure(networkConfiguration);
            networkConfiguration.persist();
        } else {
            networkConfiguration = new NetworkAssistedEngineConfiguration();
            if (isValidIpAddressOrHostName(networkConfiguration.getServerName()) && isValidPortNumber(String.valueOf(networkConfiguration.getServerPort()))) {
                autoConnect = networkConfiguration.isAutoConnect();
                if (autoConnect) {
                    networkEngine.configure(networkConfiguration);
                }
            }
        }

        if (autoConnect) {
            coldStart = false;
            networkEngine.connect();
            return true;
        }

        // no network settings dialogue after startup
        if (coldStart) {
            coldStart = false;
            return true;
        }
        return requestConnectionSettings();
    }

    private boolean requestConnectionSettings() {
        networkConfiguration = new NetworkAssistedEngineConfiguration();
        ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(networkConfiguration);

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

    private String validateIpAddress(String ipAddress) {
        if (ipAddress.isEmpty()) {
            return translate("connection.settings.emptyIpAddress");
        }
        return isValidIpAddressOrHostName(ipAddress.trim()) ? null : translate("connection.settings.invalidIpAddress");
    }

    private String validatePortNumber(String portNumber) {
        if (portNumber.isEmpty()) {
            return translate("connection.settings.emptyPortNumber");
        }
        return isValidPortNumber(portNumber.trim()) ? null : translate("connection.settings.invalidPortNumber");
    }

    private void applyConnectionSettings(ConnectionSettingsDialog connectionSettingsDialog) {
        final NetworkAssistedEngineConfiguration newConfiguration;
        String token = connectionSettingsDialog.getToken().trim();
        if (!token.isEmpty()) {
            final Cursor cursor = frame.getCursor();
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String connectionParams = null;
            try {
                connectionParams = resolveToken(tokenServerUrl, token);
            } catch (IOException | InterruptedException ex){
                Log.warn("Could not resolve token " + token);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            Log.debug("Connection params " + connectionParams);
            newConfiguration = extractConfiguration(connectionParams);
            frame.setCursor(cursor);
        } else {
            newConfiguration = new NetworkAssistedEngineConfiguration(connectionSettingsDialog.getIpAddress().trim(),
                    Integer.parseInt(connectionSettingsDialog.getPortNumber().trim()));
        }
        if (newConfiguration != null && !newConfiguration.equals(networkConfiguration)) {
            networkConfiguration = newConfiguration;
            networkConfiguration.persist();
            networkEngine.configure(networkConfiguration);
        }
        Log.info("NetworkConfiguration " + networkConfiguration);
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
        stopAction.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.STOP_LARGE));
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
        startAction.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.START_LARGE));
        return startAction;
    }

    private Action createConnectionSettingsAction() {
        final Action conf = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame networkFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());
                final Font titleFont = new Font("Sans Serif", Font.BOLD, 14);

                final JPanel panel = new JPanel();
                panel.setLayout(new GridBagLayout());

                final JLabel hostLbl = new JLabel(translate("assistant"));
                hostLbl.setFont(titleFont);

                GridBagConstraints gc0 = new GridBagConstraints();
                gc0.fill = HORIZONTAL;
                gc0.gridx = 0;
                gc0.gridy = 0;
                panel.add(hostLbl, gc0);

                final JPanel assistantPanel = new JPanel(new GridLayout(4, 2, 10, 0));
                assistantPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
                final JLabel addressLbl = new JLabel(translate("connection.settings.assistantIpAddress"));
                final JTextField addressTextField = new JTextField(networkConfiguration.getServerName());
                assistantPanel.add(addressLbl);
                assistantPanel.add(addressTextField);
                final JLabel portNumberLbl = new JLabel(translate("connection.settings.assistantPortNumber"));
                final JTextField portNumberTextField = new JTextField(format("%d", networkConfiguration.getServerPort()));
                assistantPanel.add(portNumberLbl);
                assistantPanel.add(portNumberTextField);
                final JCheckBox autoConnectCheckBox = new JCheckBox(translate("connection.settings.autoConnect"));
                autoConnectCheckBox.setSelected(networkConfiguration.isAutoConnect());
                assistantPanel.add(autoConnectCheckBox);

                GridBagConstraints gc1 = new GridBagConstraints();
                gc1.fill = HORIZONTAL;
                gc1.gridx = 0;
                gc1.gridy = 1;
                panel.add(assistantPanel, gc1);

                final JLabel tokenServerLbl = new JLabel(translate("token.server"));
                tokenServerLbl.setFont(titleFont);

                GridBagConstraints gc2 = new GridBagConstraints();
                gc2.fill = HORIZONTAL;
                gc2.gridx = 0;
                gc2.gridy = 2;
                panel.add(tokenServerLbl, gc2);

                final JPanel tokenPanel = new JPanel(new GridLayout(3, 2, 10, 0));
                tokenPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));

                final ButtonGroup tokenRadioGroup = new ButtonGroup();
                final JRadioButton defaultTokenRadio = new JRadioButton(translate("token.default.server"));
                defaultTokenRadio.setActionCommand("default");
                final JRadioButton customTokenRadio = new JRadioButton(translate("token.custom.server"));
                customTokenRadio.setActionCommand("custom");
                tokenRadioGroup.add(defaultTokenRadio);
                tokenRadioGroup.add(customTokenRadio);

                String currentTokenServer = networkConfiguration.getTokenServerUrl();
                if (currentTokenServer.isEmpty() || currentTokenServer.equals(DEFAULT_TOKEN_SERVER_URL)) {
                    currentTokenServer = "";
                    defaultTokenRadio.setSelected(true);
                } else {
                    customTokenRadio.setSelected(true);
                }

                final JTextField defaultTokenTextField = new JTextField();
                defaultTokenTextField.setText(DEFAULT_TOKEN_SERVER_URL);
                defaultTokenTextField.setEditable(false);
                tokenPanel.add(defaultTokenRadio);
                tokenPanel.add(defaultTokenTextField);

                final JTextField customTokenTextField = new JTextField();
                customTokenTextField.setText(currentTokenServer);
                customTokenRadio.addActionListener(evt -> customTokenTextField.requestFocus());
                tokenPanel.add(customTokenRadio);
                tokenPanel.add(customTokenTextField);

                GridBagConstraints gc3 = new GridBagConstraints();
                gc3.fill = HORIZONTAL;
                gc3.gridx = 0;
                gc3.gridy = 3;
                panel.add(tokenPanel, gc3);

                final boolean ok = DialogFactory.showOkCancel(networkFrame, translate("connection.network"), panel, true, () -> {
                    final String ipAddress = addressTextField.getText();
                    if (ipAddress.isEmpty()) {
                        return translate("connection.settings.emptyIpAddress");
                    } else if (!isValidIpAddressOrHostName(ipAddress)) {
                        return translate("connection.settings.invalidIpAddress");
                    }
                    final String portNumber = portNumberTextField.getText();
                    if (portNumber.isEmpty()) {
                        return translate("connection.settings.emptyPortNumber");
                    } else if (!isValidPortNumber(portNumber)) {
                        return translate("connection.settings.invalidPortNumber");
                    }
                    if (tokenRadioGroup.getSelection().getActionCommand().equals("custom") && !isValidUrl(customTokenTextField.getText())) {
                        return translate("connection.settings.invalidTokenServer");
                    }
                    return null;
                });

                if (ok) {
                    final String newTokenServerUrl = tokenRadioGroup.getSelection().getActionCommand().equals("custom") &&
                            isValidUrl(customTokenTextField.getText()) ? customTokenTextField.getText() : "";

                    if (newTokenServerUrl.isEmpty()) {
                        System.clearProperty("dayon.custom.tokenServer");
                    } else {
                        System.setProperty("dayon.custom.tokenServer", newTokenServerUrl);
                    }

                    final NetworkAssistedEngineConfiguration newNetworkConfiguration = new NetworkAssistedEngineConfiguration(
                            addressTextField.getText(), Integer.parseInt(portNumberTextField.getText()), autoConnectCheckBox.isSelected(), newTokenServerUrl);

                    if (!newNetworkConfiguration.equals(networkConfiguration)) {
                        networkConfiguration = newNetworkConfiguration;
                        networkConfiguration.persist();
                    }
                }
            }
        };
        conf.putValue(Action.SHORT_DESCRIPTION, translate("connection.settings"));
        conf.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.NETWORK_SETTINGS));
        return conf;
    }

    private class NetWorker extends SwingWorker<String, String> {
        @Override
        protected String doInBackground() {
            if (isConfigured() && !isCancelled()) {
                frame.onConnecting(networkConfiguration.getServerName(), networkConfiguration.getServerPort());
                networkEngine.configure(networkConfiguration);
                networkEngine.connect();
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
    }

    private void stop() {
        stop(networkConfiguration.getServerName());
    }

    private void stop(String serverName) {
        Log.info(format("Assisted stop [%s]", serverName));
        if (networkEngine != null && networkEngine.getConfiguration().getServerName().equals(serverName)) {
            networkEngine.farewell();
            networkEngine.cancel();
            networkEngine = null;

            if (captureEngine != null) {
                captureEngine.stop();
                captureEngine = null;
            }
            if (compressorEngine != null) {
                compressorEngine.stop();
                compressorEngine = null;
            }
        }
        frame.onDisconnecting();
    }

    private NetworkAssistedEngineConfiguration extractConfiguration(String connectionParams) {
        if (connectionParams != null) {
            int portStart = connectionParams.lastIndexOf('*');
            if (portStart > 0) {
                String address = connectionParams.substring(0, portStart);
                String port = connectionParams.substring(portStart + 1);
                if (isValidIpAddressOrHostName(address) && isValidPortNumber(port)) {
                    return new NetworkAssistedEngineConfiguration(address, Integer.parseInt(port));
                }
            }
        }
        return null;
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        Log.error("Lost clipboard ownership");
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
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable content = clipboard.getContents(this);

        if (content == null) return;

        try {
            if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // noinspection unchecked
                List<File> files = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    final long totalFilesSize = FileUtilities.calculateTotalFileSize(files);
                    Log.debug("Clipboard contains files with size: %s", () -> String.valueOf(totalFilesSize));
                    networkEngine.sendClipboardFiles(files, totalFilesSize, files.get(0).getParent());
                    return;
                }
            } else if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = valueOf(clipboard.getData(DataFlavor.stringFlavor));
                Log.debug("Clipboard contains text: " + text);
                networkEngine.sendClipboardText(text);
                return;
            } else if (content.isDataFlavorSupported(DataFlavor.imageFlavor) ){
                final BufferedImage image = (BufferedImage) clipboard.getData(DataFlavor.imageFlavor);
                Log.debug("Clipboard contains graphics: %s", () -> format("%dx%d", image.getWidth(), image.getHeight()));
                networkEngine.sendClipboardGraphic(new TransferableImage(image));
                return;
            } else {
                Log.debug("Clipboard contains no supported data");
            }
        } catch (IOException | UnsupportedFlavorException ex) {
            Log.error("Clipboard error " + ex.getMessage());
        }
        String text = "\uD83E\uDD84";
        Log.debug("Sending a unicorn: " + text);
        networkEngine.sendClipboardText(text);
    }

    private void onScreenshotRequested(){
        Log.info("Screenshot request received");
        try {
            networkEngine.setClipboardContents(new Robot().createScreenCapture(ScreenUtilities.getSharedScreenSize()), this);
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
            frame.onConnecting(serverName, serverPort);
        }

        @Override
        public void onHostNotFound(String serverName) {
            stop(serverName);
            frame.onHostNotFound(serverName);
        }

        @Override
        public void onConnectionTimeout(String serverName, int serverPort) {
            stop(serverName);
            frame.onConnectionTimeout(serverName, serverPort);
        }

        @Override
        public void onRefused(String serverName, int serverPort) {
            stop(serverName);
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
            stop(getNetworkConfiguration().getServerName());
            frame.onDisconnecting();
        }
    }
}
