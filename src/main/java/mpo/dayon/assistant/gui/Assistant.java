package mpo.dayon.assistant.gui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import mpo.dayon.assistant.control.ControlEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngineListener;
import mpo.dayon.common.gui.common.*;
import mpo.dayon.common.monitoring.counter.*;
import mpo.dayon.assistant.network.NetworkAssistantConfiguration;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.network.NetworkAssistantEngineListener;
import mpo.dayon.assistant.utils.NetworkUtilities;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkMouseLocationMessageHandler;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.utils.FileUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static mpo.dayon.common.utils.SystemUtilities.*;

public class Assistant implements Configurable<AssistantConfiguration>, ClipboardOwner {

    private final NetworkAssistantEngine network;

    private final ControlEngine control;

    private final BitCounter receivedBitCounter;

    private final TileCounter receivedTileCounter;

    private final SkippedTileCounter skippedTileCounter;

    private final MergedTileCounter mergedTileCounter;

    private final CaptureCompressionCounter captureCompressionCounter;

    private AssistantFrame frame;

    private AssistantConfiguration configuration;

    private NetworkAssistantConfiguration networkConfiguration;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private CompressorEngineConfiguration compressorEngineConfiguration;

    private final Object prevBufferLOCK = new Object();

    private byte[] prevBuffer = null;

    private int prevWidth = -1;

    private int prevHeight = -1;

    private final Set<Counter<?>> counters;

    private final AtomicBoolean fitToScreenActivated  = new AtomicBoolean(false);

    public Assistant() {
        receivedBitCounter = new BitCounter("receivedBits", Babylon.translate("networkBandwidth"));
        receivedBitCounter.start(1000);

        receivedTileCounter = new TileCounter("receivedTiles", Babylon.translate("receivedTileNumber"));
        receivedTileCounter.start(1000);

        skippedTileCounter = new SkippedTileCounter("skippedTiles", Babylon.translate("skippedCaptureNumber"));
        skippedTileCounter.start(1000);

        mergedTileCounter = new MergedTileCounter("mergedTiles", Babylon.translate("mergedCaptureNumber"));
        mergedTileCounter.start(1000);

        captureCompressionCounter = new CaptureCompressionCounter("captureCompression", Babylon.translate("captureCompression"));
        captureCompressionCounter.start(1000);

        counters = new HashSet<>(Arrays.asList(receivedBitCounter, receivedTileCounter, skippedTileCounter, mergedTileCounter, captureCompressionCounter));

        DeCompressorEngine decompressor = new DeCompressorEngine();
        decompressor.addListener(new MyDeCompressorEngineListener());
        decompressor.start(8);

        NetworkMouseLocationMessageHandler mouseHandler = mouse -> frame.onMouseLocationUpdated(mouse.getX(), mouse.getY());

        network = new NetworkAssistantEngine(decompressor, mouseHandler, this);

        networkConfiguration = new NetworkAssistantConfiguration();
        network.configure(networkConfiguration);
        network.addListener(new MyNetworkAssistantEngineListener());

        control = new ControlEngine(network);
        control.start();

        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();
    }

    @Override
    public void configure(AssistantConfiguration configuration) {
        this.configuration = configuration;

        final String lnf = configuration.getLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn("Could not set the [" + lnf + "] L&F!", ex);
        }
    }

    public void start() {
        frame = new AssistantFrame(createAssistantActions(), counters);

        FatalErrorHandler.attachFrame(frame);

        frame.addListener(control);
        frame.setVisible(true);
    }

    private AssistantActions createAssistantActions() {
        AssistantActions actions = new AssistantActions();
        actions.setIpAddressAction(createWhatIsMyIpAction());
        actions.setNetworkConfigurationAction(createNetworkAssistantConfigurationAction());
        actions.setCaptureEngineConfigurationAction(createCaptureConfigurationAction());
        actions.setCompressionEngineConfigurationAction(createComressionConfigurationAction());
        actions.setResetAction(createResetAction());
        actions.setLookAndFeelAction(createSwitchLookAndFeelAction());
        actions.setToggleFitScreenAction(createToggleFixScreenAction());
        actions.setRemoteClipboardRequestAction(createRemoteClipboardRequestAction());
        actions.setRemoteClipboardSetAction(createRemoteClipboardUpdateAction());
        actions.setStartAction(new AssistantStartAction(this));
        actions.setStopAction(new AssistantStopAction(this));
        return actions;
    }

    void startNetwork() {
        network.start();
    }

    void stopNetwork() {
        network.cancel();
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        Log.error("Lost clipboard ownership");
    }

    private Action createWhatIsMyIpAction() {
        final Action ip = new AbstractAction() {
            private String publicIp;

            @Override
            public void actionPerformed(ActionEvent ev) {
                final JButton button = (JButton) ev.getSource();

                final JPopupMenu choices = new JPopupMenu();

                if (publicIp == null) {
                    final JMenuItem menuItem = new JMenuItem(Babylon.translate("retrieveMe"));
                    menuItem.addActionListener(ev16 -> {
                        final Cursor cursor = frame.getCursor();
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                        try {
                            final URL url = new URL("http://dayonhome.sourceforge.net/whatismyip.php");
                            final InputStream in = url.openStream();
                            try (final BufferedReader lines = new BufferedReader(new InputStreamReader(url.openStream()))) {
                                publicIp = lines.readLine();
                            }

                            safeClose(in);
                        } catch (IOException ex) {
                            Log.error("What is my IP error!", ex);
                            JOptionPane.showMessageDialog(frame, Babylon.translate("ipAddress.msg1"), Babylon.translate("ipAddress"),
                                    JOptionPane.ERROR_MESSAGE);
                        } finally {
                            frame.setCursor(cursor);
                        }

                        if (publicIp != null) {
                            button.setText(publicIp);
                        }
                    });
                    choices.add(menuItem);
                } else {
                    final JMenuItem menuItem = new JMenuItem(Babylon.translate("ipAddressPublic", publicIp));
                    menuItem.addActionListener(ev15 -> button.setText(publicIp));
                    choices.add(menuItem);
                }

                final List<String> addrs = NetworkUtilities.getInetAddresses();
                for (String addr : addrs) {
                    final JMenuItem menuItem = new JMenuItem(addr);
                    menuItem.addActionListener(ev14 -> button.setText(menuItem.getText()));
                    choices.add(menuItem);
                }

                choices.addSeparator();

                choices.add(getjMenuItemCopyIpAndPort(button));

                choices.addSeparator();
                final JMenuItem help = getjMenuItemHelp();
                choices.add(help);

                // -- display the menu
                // ---------------------------------------------------------------------------------

                final Point where = MouseInfo.getPointerInfo().getLocation();

                SwingUtilities.convertPointFromScreen(where, frame);
                choices.show(frame, where.x, where.y);

                final Point choicesLocation = choices.getLocationOnScreen();
                final Point frameLocation = frame.getLocationOnScreen();
                final int xOffset = (choicesLocation.x + choices.getWidth()) - (frameLocation.x + frame.getWidth() - frame.getInsets().right);

                final Point toolbarLocation = frame.getToolBar().getLocationOnScreen();
                final int yOffset = toolbarLocation.y + frame.getToolBar().getHeight() - choicesLocation.y;

                choices.setLocation(choicesLocation.x - xOffset, choicesLocation.y + yOffset);
            }
        };

        ip.putValue(Action.NAME, "whatIsMyIpAddress");
        ip.putValue("DISPLAY_NAME", network.getLocalhost()); // always a selection
        // ...
        ip.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("ipAddress.msg1"));

        return ip;
    }

    @NotNull
    private JMenuItem getjMenuItemHelp() {
        final JMenuItem help = new JMenuItem(Babylon.translate("help"));
        help.addActionListener(ev1 -> {
            if (isSnapped()) {
                try {
                    new ProcessBuilder("dayon.browser", getQuickStartURI(FrameType.ASSISTANT).toString()).start();
                } catch (URISyntaxException | IOException ex) {
                    Log.warn("Help Error!", ex);
                }
            } else if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(getQuickStartURI(FrameType.ASSISTANT));
                } catch (URISyntaxException | IOException ex) {
                    Log.warn("Help Error!", ex);
                }
            }
        });
        return help;
    }

    @NotNull
    private JMenuItem getjMenuItemCopyIpAndPort(JButton button) {
        final JMenuItem menuItem = new JMenuItem(Babylon.translate("copy.msg"));
        menuItem.addActionListener(ev12 -> {
            final String url = button.getText() + " " + networkConfiguration.getPort();

            final StringSelection value = new StringSelection(url);
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(value, value);
        });
        return menuItem;
    }

    private Action createNetworkAssistantConfigurationAction() {
        final Action exit = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame networkFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

                final JPanel pane = new JPanel();

                pane.setLayout(new GridLayout(1, 2, 10, 10));

                final JLabel portNumberLbl = new JLabel(Babylon.translate("connection.settings.portNumber"));
                portNumberLbl.setToolTipText(Babylon.translate("connection.settings.portNumber.tooltip"));

                final JTextField portNumberTextField = new JTextField();
                portNumberTextField.setText(String.valueOf(networkConfiguration.getPort()));

                pane.add(portNumberLbl);
                pane.add(portNumberTextField);

                final boolean ok = DialogFactory.showOkCancel(networkFrame, Babylon.translate("connection.network.settings"), pane, () -> {
                    final String portNumber = portNumberTextField.getText();
                    if (portNumber.isEmpty()) {
                        return Babylon.translate("connection.settings.emptyPortNumber");
                    }
                    return isValidPortNumber(portNumber) ? null : Babylon.translate("connection.settings.invalidPortNumber");
                });

                if (ok) {
                    final NetworkAssistantConfiguration xnetworkConfiguration = new NetworkAssistantConfiguration(
                            Integer.parseInt(portNumberTextField.getText()));

                    if (!xnetworkConfiguration.equals(networkConfiguration)) {
                        networkConfiguration = xnetworkConfiguration;
                        networkConfiguration.persist();

                        network.reconfigure(networkConfiguration);
                    }
                }
            }
        };

        exit.putValue(Action.NAME, "networkAssistantConfiguration");
        exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("connection.network.settings"));
        exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.NETWORK_SETTINGS));

        return exit;
    }

    private Action createRemoteClipboardRequestAction() {
        final Action getRemoteClipboard = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                sendRemoteClipboardRequest();
            }
        };

        getRemoteClipboard.putValue(Action.NAME, "getClipboard");
        getRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("clipboard.getRemote"));
        getRemoteClipboard.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.DOWN));

        return getRemoteClipboard;
    }

    private Action createRemoteClipboardUpdateAction() {
        final Action setRemoteClipboard = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                sendLocalClipboard();
            }
        };

        setRemoteClipboard.putValue(Action.NAME, "setClipboard");
        setRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("clipboard.setRemote"));
        setRemoteClipboard.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.UP));

        return setRemoteClipboard;
    }

    private void sendLocalClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable content = clipboard.getContents(this);

        if (content == null) return;

        try {
            if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // noinspection unchecked
                List<File> files = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    final long totalFilesSize = FileUtilities.calculateTotalFileSize(files);
                    Log.debug("Clipboard contains files with size: " + totalFilesSize );
                    // Ok as very few of that (!)
                    new Thread(() -> network.setRemoteClipboardFiles(files, totalFilesSize, files.get(0).getParent()), "setRemoteClipboardFiles").start();
                    frame.onClipboardSending();
                }
            } else if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                // noinspection unchecked
                String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                Log.debug("Clipboard contains text: " + text);
                // Ok as very few of that (!)
                new Thread(() -> network.setRemoteClipboardText(text, text.getBytes().length), "setRemoteClipboardText").start();
                frame.onClipboardSending();
            } else {
                Log.debug("Clipboard contains no supported data");
            }
        } catch (IOException | UnsupportedFlavorException ex) {
            Log.error("Clipboard error " + ex.getMessage());
            frame.onClipboardSent();
        }
    }

    /**
     * Should not block (!)
     */
    private void sendRemoteClipboardRequest() {
        Log.info("Requesting remote clipboard");
        frame.onClipboardRequested();
        // Ok as very few of that (!)
        new Thread(network::sendRemoteClipboardRequest, "RemoteClipboardRequest").start();
    }

    private Action createCaptureConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame captureFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

                final JPanel pane = new JPanel();

                pane.setLayout(new GridLayout(2, 2, 10, 10));

                final JLabel tickLbl = new JLabel(Babylon.translate("tick"));
                tickLbl.setToolTipText(Babylon.translate("tick.tooltip"));

                final JTextField tickTextField = new JTextField();
                tickTextField.setText(String.valueOf(captureEngineConfiguration.getCaptureTick()));

                pane.add(tickLbl);
                pane.add(tickTextField);

                final JLabel grayLevelsLbl = new JLabel(Babylon.translate("grays"));
                final JComboBox<Gray8Bits> grayLevelsCb = new JComboBox<>(Gray8Bits.values());
                grayLevelsCb.setSelectedItem(captureEngineConfiguration.getCaptureQuantization());

                pane.add(grayLevelsLbl);
                pane.add(grayLevelsCb);

                final boolean ok = DialogFactory.showOkCancel(captureFrame, Babylon.translate("capture.settings"), pane, () -> {
                    final String tick = tickTextField.getText();
                    if (tick.isEmpty()) {
                        return Babylon.translate("tick.msg1");
                    }

                    try {
                        Integer.valueOf(tick);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("tick.msg2");
                    }

                    return null;
                });

                if (ok) {
                    final CaptureEngineConfiguration newCaptureEngineConfiguration = new CaptureEngineConfiguration(Integer.parseInt(tickTextField.getText()),
                            (Gray8Bits) grayLevelsCb.getSelectedItem());

                    if (!newCaptureEngineConfiguration.equals(captureEngineConfiguration)) {
                        captureEngineConfiguration = newCaptureEngineConfiguration;
                        captureEngineConfiguration.persist();

                        sendCaptureConfiguration(captureEngineConfiguration);
                    }
                }
            }
        };

        configure.putValue(Action.NAME, "configureCapture");
        configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("capture.settings.msg"));
        configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.CAPTURE_SETTINGS));

        return configure;
    }

    /**
     * Should not block (!)
     */
    private void sendCaptureConfiguration(final CaptureEngineConfiguration captureEngineConfiguration) {
        // Ok as very few of that (!)
        new Thread(() -> network.sendCaptureConfiguration(captureEngineConfiguration), "CaptureEngineSettingsSender").start();
    }

    private Action createComressionConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame compressionFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

                final JPanel pane = new JPanel();
                pane.setLayout(new GridLayout(4, 2, 10, 10));

                final JLabel methodLbl = new JLabel(Babylon.translate("compression.method"));
                // testing only: final JComboBox<CompressionMethod> methodCb = new JComboBox<>(CompressionMethod.values());
                final JComboBox<CompressionMethod> methodCb = new JComboBox<>(Stream.of(CompressionMethod.values()).filter(e -> !e.equals(CompressionMethod.NONE)).toArray(CompressionMethod[]::new));
                methodCb.setSelectedItem(compressorEngineConfiguration.getMethod());

                pane.add(methodLbl);
                pane.add(methodCb);

                final JLabel useCacheLbl = new JLabel(Babylon.translate("compression.cache.usage"));
                final JCheckBox useCacheCb = new JCheckBox();
                useCacheCb.setSelected(compressorEngineConfiguration.useCache());

                pane.add(useCacheLbl);
                pane.add(useCacheCb);

                final JLabel maxSizeLbl = new JLabel(Babylon.translate("compression.cache.max"));
                maxSizeLbl.setToolTipText(Babylon.translate("compression.cache.max.tooltip"));
                final JTextField maxSizeTf = new JTextField(String.valueOf(compressorEngineConfiguration.getCacheMaxSize()));

                pane.add(maxSizeLbl);
                pane.add(maxSizeTf);

                final JLabel purgeSizeLbl = new JLabel(Babylon.translate("compression.cache.purge"));
                purgeSizeLbl.setToolTipText(Babylon.translate("compression.cache.purge.tooltip"));
                final JTextField purgeSizeTf = new JTextField(String.valueOf(compressorEngineConfiguration.getCachePurgeSize()));

                pane.add(purgeSizeLbl);
                pane.add(purgeSizeTf);

                useCacheCb.addActionListener(ev1 -> {
                    maxSizeLbl.setEnabled(useCacheCb.isSelected());
                    maxSizeTf.setEnabled(useCacheCb.isSelected());
                    purgeSizeLbl.setEnabled(useCacheCb.isSelected());
                    purgeSizeTf.setEnabled(useCacheCb.isSelected());
                });

                maxSizeLbl.setEnabled(useCacheCb.isSelected());
                maxSizeTf.setEnabled(useCacheCb.isSelected());
                purgeSizeLbl.setEnabled(useCacheCb.isSelected());
                purgeSizeTf.setEnabled(useCacheCb.isSelected());

                final boolean ok = DialogFactory.showOkCancel(compressionFrame, Babylon.translate("compression.settings"), pane, () -> {
                    final String max = maxSizeTf.getText();
                    if (max.isEmpty()) {
                        return Babylon.translate("compression.cache.max.msg1");
                    }

                    final int maxValue;

                    try {
                        maxValue = Integer.parseInt(max);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("compression.cache.max.msg2");
                    }

                    if (maxValue <= 0) {
                        return Babylon.translate("compression.cache.max.msg3");
                    }

                    return validatePurgeValue(purgeSizeTf, maxValue);
                });

                if (ok) {
                    final CompressorEngineConfiguration newCompressorEngineConfiguration = new CompressorEngineConfiguration((CompressionMethod) methodCb.getSelectedItem(),
                            useCacheCb.isSelected(), Integer.parseInt(maxSizeTf.getText()), Integer.parseInt(purgeSizeTf.getText()));

                    if (!newCompressorEngineConfiguration.equals(compressorEngineConfiguration)) {
                        compressorEngineConfiguration = newCompressorEngineConfiguration;
                        compressorEngineConfiguration.persist();

                        sendCompressorConfiguration(compressorEngineConfiguration);
                    }
                }
            }
        };

        configure.putValue(Action.NAME, "configureCompression");
        configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("compression.settings.msg"));
        configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.COMPRESSION_SETTINGS));

        return configure;
    }

    @Nullable
    private String validatePurgeValue(JTextField purgeSizeTf, int maxValue) {
        final String purge = purgeSizeTf.getText();
        if (purge.isEmpty()) {
            return Babylon.translate("compression.cache.purge.msg1");
        }

        final int purgeValue;

        try {
            purgeValue = Integer.parseInt(purge);
        } catch (NumberFormatException ex) {
            return Babylon.translate("compression.cache.purge.msg2");
        }


        if (purgeValue <= 0) {
            return Babylon.translate("compression.cache.purge.msg3");
        }

        if (purgeValue >= maxValue) {
            return Babylon.translate("compression.cache.purge.msg4");
        }

        return null;
    }

    /**
     * Should not block (!)
     */
    private void sendCompressorConfiguration(final CompressorEngineConfiguration compressorEngineConfiguration) {
        // Ok as very few of that (!)
        new Thread(() -> network.sendCompressorConfiguration(compressorEngineConfiguration), "CompressorEngineSettingsSender").start();
    }

    private Action createResetAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                // Currently making a RESET within the assisted ...
                sendCaptureConfiguration(captureEngineConfiguration);
            }
        };

        configure.putValue(Action.NAME, "resetCapture");
        configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("capture.reset"));
        configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.RESET_CAPTURE));

        return configure;
    }

    private Action createToggleFixScreenAction() {
        final Action fitScreen = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                fitToScreenActivated.set(!fitToScreenActivated.get());
                if (!fitToScreenActivated.get()) {
                    frame.resetFactors();
                }
                frame.repaint();
            }
        };

        fitScreen.putValue(Action.NAME, "toggleScreenMode");
        fitScreen.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("toggle.screen.mode"));
        fitScreen.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.FIT));

        return fitScreen;
    }

    private Action createSwitchLookAndFeelAction() {
        final Action exit = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                final JPopupMenu choices = new JPopupMenu();

                final LookAndFeel current = UIManager.getLookAndFeel();
                choices.add(new JMenuItem(current.getName()));
                choices.addSeparator();

                for (final UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (info.getName().equals(current.getName())) {
                        continue;
                    }

                    final JMenuItem mi = new JMenuItem(info.getName());

                    mi.addActionListener(ev1 -> switchLookAndFeel(info));
                    choices.add(mi);
                }

                final Point where = MouseInfo.getPointerInfo().getLocation();
                final JComponent caller = (JComponent) ev.getSource();

                SwingUtilities.convertPointFromScreen(where, caller);
                choices.show(caller, 0, caller.getHeight());
            }
        };

        exit.putValue(Action.NAME, "lf");
        exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("lnf.switch"));
        exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.LNF));

        return exit;
    }

    private void switchLookAndFeel(UIManager.LookAndFeelInfo lnf) {
        try {
            if (frame != null) {
                UIManager.setLookAndFeel(lnf.getClassName());
                SwingUtilities.updateComponentTreeUI(frame);

                configuration = new AssistantConfiguration(lnf.getClassName());
                configuration.persist();
            }
        } catch (Exception ex) {
            Log.warn("Could not set the L&F [" + lnf.getName() + "]", ex);
        }
    }

    private class MyDeCompressorEngineListener implements DeCompressorEngineListener {
        /**
         * Called from within THE de-compressor engine thread => prevBuffer
         * usage (!)
         */
        @Override
        public void onDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
            final AbstractMap.SimpleEntry<BufferedImage, byte[]> image;

            // synchronized because of the reset onStarting()
            synchronized (prevBufferLOCK) {
                image = capture.createBufferedImage(prevBuffer, prevWidth, prevHeight);

                prevBuffer = image.getValue();
                prevWidth = image.getKey().getWidth();
                prevHeight = image.getKey().getHeight();
            }

            if (fitToScreenActivated.get()) {
                Dimension frameDimension = frame.getUsableSize(prevWidth, prevHeight);
                frame.onCaptureUpdated(scaleImage(image.getKey(), frameDimension.width, frameDimension.height));
            } else {
                frame.onCaptureUpdated(image.getKey());
            }

            receivedTileCounter.add(capture.getDirtyTileCount(), cacheHits);
            skippedTileCounter.add(capture.getSkipped());
            mergedTileCounter.add(capture.getMerged());

            captureCompressionCounter.add(capture.getDirtyTileCount(), compressionRatio);
        }

        private BufferedImage scaleImage(BufferedImage image, int width, int height) {
            double scaleX = (double)width/image.getWidth();
            double scaleY = (double)height/image.getHeight();
            AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
            AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);
            return bilinearScaleOp.filter(image, new BufferedImage(width, height, image.getType()));
        }
    }

    private class MyNetworkAssistantEngineListener implements NetworkAssistantEngineListener {
        @Override
        public void onReady() {
            frame.onReady();
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onStarting(int port) {
            frame.onHttpStarting(port);

            synchronized (prevBufferLOCK) {
                prevBuffer = null;
                prevWidth = -1;
                prevHeight = -1;
            }
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public boolean onAccepted(Socket connection) {
            return frame.onAccepted(connection);
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onConnected(Socket connection) {
            sendCaptureConfiguration(captureEngineConfiguration);
            sendCompressorConfiguration(compressorEngineConfiguration);
            frame.onSessionStarted();
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onByteReceived(int count) {
            receivedBitCounter.add(8d * count);
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onClipboardReceived() {
            frame.onClipboardReceived();
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onClipboardSent() {
            frame.onClipboardSent();
        }

        /**
         * Should not block as called from the network receiving thread (!)
         */
        @Override
        public void onDisconnecting() {
            frame.onDisconnecting();
        }

        @Override
        public void onIOError(IOException error) {
            frame.onIOError(error);
        }
    }
}
