package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
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
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.network.FileUtilities;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

public class Assisted implements Subscriber, ClipboardOwner {
    private AssistedFrame frame;

    private NetworkAssistedEngineConfiguration configuration;

    private CaptureEngine captureEngine;

    private CompressorEngine compressorEngine;

    private NetworkAssistedEngine networkEngine;

    private boolean coldStart = true;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private final AtomicBoolean shareAllScreens = new AtomicBoolean(false);

    public void configure() {
        final String lnf = SystemUtilities.getDefaultLookAndFeel();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn("Could not set the [" + lnf + "] L&F!", ex);
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

        final NetworkControlMessageHandler controlHandler = new RobotNetworkControlMessageHandler();
        controlHandler.subscribe(this);

        networkEngine = new NetworkAssistedEngine(captureConfigurationHandler, compressorConfigurationHandler,
                controlHandler, clipboardRequestHandler, this);
        networkEngine.addListener(new MyNetworkAssistedEngineListener());

        if (frame == null) {
            frame = new AssistedFrame(new AssistedStartAction(this), new AssistedStopAction(this), createToggleMultiScreenAction());
            FatalErrorHandler.attachFrame(frame);
            KeyboardErrorHandler.attachFrame(frame);
            frame.setVisible(true);
        }
        return configureConnection(serverName, portNumber, autoConnect);
    }

    public NetworkAssistedEngineConfiguration getConfiguration() {
        return configuration;
    }

    private boolean configureConnection(String serverName, String portNumber, boolean autoConnect) {
        if (SystemUtilities.isValidIpAddressOrHostName(serverName) && SystemUtilities.isValidPortNumber(portNumber)) {
            configuration = new NetworkAssistedEngineConfiguration(serverName, Integer.parseInt(portNumber));
            Log.info("Autoconfigured " + configuration);
            networkEngine.configure(configuration);
            configuration.persist();
        } else {
            autoConnect = false;
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
        configuration = new NetworkAssistedEngineConfiguration();
        ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(configuration);

        final boolean ok = DialogFactory.showOkCancel(frame, translate("connection.settings"), connectionSettingsDialog.getTabbedPane(), false, () -> {
            final String token = connectionSettingsDialog.getToken();
            if (!token.trim().isEmpty()) {
                return SystemUtilities.isValidToken(token.trim()) ? null : translate("connection.settings.invalidToken");
            } else {

                final String ipAddress = connectionSettingsDialog.getIpAddress();
                if (ipAddress.isEmpty()) {
                    return translate("connection.settings.emptyIpAddress");
                } else if (!SystemUtilities.isValidIpAddressOrHostName(ipAddress.trim())) {
                    return translate("connection.settings.invalidIpAddress");
                }

                final String portNumber = connectionSettingsDialog.getPortNumber();
                if (portNumber.isEmpty()) {
                    return translate("connection.settings.emptyPortNumber");
                }
                return SystemUtilities.isValidPortNumber(portNumber.trim()) ? null : translate("connection.settings.invalidPortNumber");
            }
        });

        if (ok) {
            final NetworkAssistedEngineConfiguration newConfiguration;
            String token = connectionSettingsDialog.getToken().trim();
            if (!token.isEmpty()) {
                final Cursor cursor = frame.getCursor();
                frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                String connectionParams = SystemUtilities.resolveToken(token);
                Log.debug("Connection params " + connectionParams);
                newConfiguration = extractConfiguration(connectionParams);
                frame.setCursor(cursor);
            } else {
                newConfiguration = new NetworkAssistedEngineConfiguration(connectionSettingsDialog.getIpAddress().trim(),
                        Integer.parseInt(connectionSettingsDialog.getPortNumber().trim()));
            }
            if (newConfiguration != null && !newConfiguration.equals(configuration)) {
                configuration = newConfiguration;
                configuration.persist();
            }
            Log.info("Configuration " + configuration);
        } else {
            // cancel
            frame.onReady();
        }
        return ok;
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
        multiScreen.putValue(Action.NAME, "shareAllScreens");
        multiScreen.putValue(Action.SHORT_DESCRIPTION, translate("share.all.screens"));
        multiScreen.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.LNF));
        return multiScreen;
    }

    boolean start() {
        // triggers network settings dialogue
        return start(null, null, false);
    }

    void connect() {
        frame.onConnecting(configuration.getServerName(), configuration.getServerPort());
        networkEngine.configure(configuration);
        networkEngine.connect();
    }

    void stop() {
        stop(configuration.getServerName());
    }

    private void stop(String serverName) {
        Log.info(format("Assisted stop [%s]", serverName));
        if (networkEngine != null && networkEngine.getConfiguration().getServerName().equals(serverName)) {
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
            frame.onDisconnecting();
        }
    }

    private NetworkAssistedEngineConfiguration extractConfiguration(String connectionParams) {
        if (connectionParams != null) {
            int portStart = connectionParams.lastIndexOf('*');
            if (portStart > 0) {
                String address = connectionParams.substring(0, portStart);
                String port = connectionParams.substring(portStart + 1);
                if (SystemUtilities.isValidIpAddressOrHostName(address) && SystemUtilities.isValidPortNumber(port)) {
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

        final MouseEngine mouseEngine = new MouseEngine();
        mouseEngine.addListener(networkEngine);
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
        Transferable transferable = clipboard.getContents(this);

        if (transferable == null) return;

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // noinspection unchecked
                List<File> files = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    final long totalFilesSize = FileUtilities.calculateTotalFileSize(files);
                    Log.debug("Clipboard contains files with size: " + totalFilesSize);
                    networkEngine.sendClipboardFiles(files, totalFilesSize, files.get(0).getParent());
                    return;
                }
            } else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                // noinspection unchecked
                String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                Log.debug("Clipboard contains text: " + text);
                networkEngine.sendClipboardText(text, text.getBytes().length);
                return;
            } else {
                Log.debug("Clipboard contains no supported data");
            }
        } catch (IOException | UnsupportedFlavorException ex) {
            Log.error("Clipboard error " + ex.getMessage());
        }
        String text = "\uD83E\uDD84";
        Log.debug("Sending a unicorn: " + text);
        networkEngine.sendClipboardText(text, text.getBytes().length);
    }

    @Override
    public void digest(String message) {
        KeyboardErrorHandler.warn(String.valueOf(message));
    }

    void onReady() {
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
        public void onConnected() {
            frame.onConnected();
        }

        @Override
        public void onDisconnecting() {
            frame.onDisconnecting();
        }

        @Override
        public void onIOError(IOException error) {
            stop(getConfiguration().getServerName());
            frame.onDisconnecting();
        }
    }
}
