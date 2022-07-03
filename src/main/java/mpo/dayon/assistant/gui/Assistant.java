package mpo.dayon.assistant.gui;

import mpo.dayon.assistant.control.ControlEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngineListener;
import mpo.dayon.assistant.network.NetworkAssistantEngineConfiguration;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.network.NetworkAssistantEngineListener;
import mpo.dayon.assistant.utils.NetworkUtilities;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.monitoring.counter.*;
import mpo.dayon.common.network.message.NetworkMouseLocationMessageHandler;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.network.FileUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class Assistant implements ClipboardOwner {

    private static final String TOKEN_SERVER_URL = "https://fensterkitt.ch/dayon/?port=%s";
    private static final String WHATSMYIP_SERVER_URL = "https://fensterkitt.ch/dayon/whatismyip.php";
    private static final String QUICKSTART_PAGE = translate("quickstart.html");
    private static final String APP = "assistant";

    private final NetworkAssistantEngine network;

    private final ControlEngine control;

    private final BitCounter receivedBitCounter;

    private final TileCounter receivedTileCounter;

    private final SkippedTileCounter skippedTileCounter;

    private final MergedTileCounter mergedTileCounter;

    private final CaptureCompressionCounter captureCompressionCounter;

    private AssistantFrame frame;

    private AssistantActions actions;

    private AssistantConfiguration configuration;

    private NetworkAssistantEngineConfiguration networkConfiguration;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private CompressorEngineConfiguration compressorEngineConfiguration;

    private final Object prevBufferLOCK = new Object();

    private byte[] prevBuffer = null;

    private int prevWidth = -1;

    private int prevHeight = -1;

    private final Set<Counter<?>> counters;

    private final AtomicBoolean fitToScreenActivated = new AtomicBoolean(false);

    private String token;

    public Assistant() {
        receivedBitCounter = new BitCounter("receivedBits", translate("networkBandwidth"));
        receivedBitCounter.start(1000);

        receivedTileCounter = new TileCounter("receivedTiles", translate("receivedTileNumber"));
        receivedTileCounter.start(1000);

        skippedTileCounter = new SkippedTileCounter("skippedTiles", translate("skippedCaptureNumber"));
        skippedTileCounter.start(1000);

        mergedTileCounter = new MergedTileCounter("mergedTiles", translate("mergedCaptureNumber"));
        mergedTileCounter.start(1000);

        captureCompressionCounter = new CaptureCompressionCounter("captureCompression", translate("captureCompression"));
        captureCompressionCounter.start(1000);

        counters = new HashSet<>(Arrays.asList(receivedBitCounter, receivedTileCounter, skippedTileCounter, mergedTileCounter, captureCompressionCounter));

        DeCompressorEngine decompressor = new DeCompressorEngine();
        decompressor.addListener(new MyDeCompressorEngineListener());
        decompressor.start(8);

        NetworkMouseLocationMessageHandler mouseHandler = mouse -> frame.onMouseLocationUpdated(mouse.getX(), mouse.getY());
        network = new NetworkAssistantEngine(decompressor, mouseHandler, this);

        networkConfiguration = new NetworkAssistantEngineConfiguration();
        network.configure(networkConfiguration);
        network.addListener(new MyNetworkAssistantEngineListener());

        control = new ControlEngine(network);
        control.start();

        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();
    }

    public void configure() {
        this.configuration = new AssistantConfiguration();
        final String lnf = configuration.getLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn("Could not set the [" + lnf + "] L&F!", ex);
        }
    }

    public void start() {
        actions = createAssistantActions();
        frame = new AssistantFrame(actions, counters);
        FatalErrorHandler.attachFrame(frame);
        frame.addListener(control);
        frame.setVisible(true);
    }

    private AssistantActions createAssistantActions() {
        AssistantActions assistantActions = new AssistantActions();
        assistantActions.setIpAddressAction(createWhatIsMyIpAction());
        assistantActions.setNetworkConfigurationAction(createNetworkAssistantConfigurationAction());
        assistantActions.setCaptureEngineConfigurationAction(createCaptureConfigurationAction());
        assistantActions.setCompressionEngineConfigurationAction(createCompressionConfigurationAction());
        assistantActions.setResetAction(createResetAction());
        assistantActions.setSettingsAction(createSettingsAction());
        assistantActions.setTokenAction(createTokenAction());
        assistantActions.setToggleFitScreenAction(createToggleFixScreenAction());
        assistantActions.setRemoteClipboardRequestAction(createRemoteClipboardRequestAction());
        assistantActions.setRemoteClipboardSetAction(createRemoteClipboardUpdateAction());
        assistantActions.setStartAction(new AssistantStartAction(this));
        assistantActions.setStopAction(new AssistantStopAction(this));
        return assistantActions;
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
                    final JMenuItem menuItem = new JMenuItem(translate("retrieveMe"));
                    menuItem.addActionListener(ev16 -> {
                        final Cursor cursor = frame.getCursor();
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        try {
                            final URL url = new URL(WHATSMYIP_SERVER_URL);
                            try (final BufferedReader lines = new BufferedReader(new InputStreamReader(url.openStream()))) {
                                publicIp = lines.readLine();
                            }
                        } catch (IOException ex) {
                            Log.error("Could not determine public IP", ex);
                            JOptionPane.showMessageDialog(frame, translate("ipAddress.msg1"), translate("ipAddress"),
                                    JOptionPane.ERROR_MESSAGE);
                        } finally {
                            frame.setCursor(cursor);
                        }
                        if (publicIp != null) {
                            button.setText(publicIp);
                            button.setFont(new Font("Sans Serif", Font.PLAIN, 18));
                        }
                    });
                    choices.add(menuItem);
                } else {
                    final JMenuItem menuItem = new JMenuItem(translate("ipAddressPublic", publicIp));
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
                choices.add(getJMenuItemCopyIpAndPort(button));
                choices.addSeparator();
                final JMenuItem help = getJMenuItemHelp();
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
        ip.putValue(Action.SHORT_DESCRIPTION, translate("ipAddress.msg1"));
        return ip;
    }

    private JMenuItem getJMenuItemHelp() {
        final JMenuItem help = new JMenuItem(translate("help"));
        help.addActionListener(ev1 -> {
            try {
                if (isSnapped()) {
                    new ProcessBuilder(getSnapBrowserCommand(), getQuickStartURI(QUICKSTART_PAGE, APP).toString()).start();
                } else if (Desktop.isDesktopSupported()) {
                    final Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.BROWSE)) {
                        desktop.browse(getQuickStartURI(QUICKSTART_PAGE, APP));
                    } else if (isFlat()) {
                        new ProcessBuilder(FLATPACK_BROWSER, getQuickStartURI(QUICKSTART_PAGE, APP).toString()).start();
                    }
                }
            } catch (IOException ex) {
                Log.warn(ex.getMessage());
            }
        });
        return help;
    }

    private JMenuItem getJMenuItemCopyIpAndPort(JButton button) {
        final JMenuItem menuItem = new JMenuItem(translate("copy.msg"));
        menuItem.addActionListener(ev12 -> {
            final String url = button.getText() + " " + networkConfiguration.getPort();
            final StringSelection value = new StringSelection(url);
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(value, this);
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
                final JLabel portNumberLbl = new JLabel(translate("connection.settings.portNumber"));
                portNumberLbl.setToolTipText(translate("connection.settings.portNumber.tooltip"));
                final JTextField portNumberTextField = new JTextField();
                portNumberTextField.setText(String.valueOf(networkConfiguration.getPort()));
                pane.add(portNumberLbl);
                pane.add(portNumberTextField);

                final boolean ok = DialogFactory.showOkCancel(networkFrame, translate("connection.network"), pane, true, () -> {
                    final String portNumber = portNumberTextField.getText();
                    if (portNumber.isEmpty()) {
                        return translate("connection.settings.emptyPortNumber");
                    }
                    return isValidPortNumber(portNumber) ? null : translate("connection.settings.invalidPortNumber");
                });

                if (ok) {
                    final NetworkAssistantEngineConfiguration xnetworkConfiguration = new NetworkAssistantEngineConfiguration(
                            Integer.parseInt(portNumberTextField.getText()));

                    if (!xnetworkConfiguration.equals(networkConfiguration)) {
                        networkConfiguration = xnetworkConfiguration;
                        networkConfiguration.persist();

                        network.reconfigure(networkConfiguration);
                    }
                }
            }
        };

        exit.putValue(Action.NAME, margin(translate("connection.network")));
        exit.putValue(Action.SHORT_DESCRIPTION, translate("connection.settings"));
        exit.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.NETWORK_SETTINGS));
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
        getRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, translate("clipboard.getRemote"));
        getRemoteClipboard.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.DOWN));
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
        setRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, translate("clipboard.setRemote"));
        setRemoteClipboard.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.UP));
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
                    Log.debug("Clipboard contains files with size: " + totalFilesSize);
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

                final JLabel tickLbl = new JLabel(translate("tick"));
                tickLbl.setToolTipText(translate("tick.tooltip"));
                final JTextField tickTextField = new JTextField();
                tickTextField.setText(String.valueOf(captureEngineConfiguration.getCaptureTick()));
                pane.add(tickLbl);
                pane.add(tickTextField);

                final JLabel grayLevelsLbl = new JLabel(translate("grays"));
                final JComboBox<Gray8Bits> grayLevelsCb = new JComboBox<>(Gray8Bits.values());
                grayLevelsCb.setSelectedItem(captureEngineConfiguration.getCaptureQuantization());
                pane.add(grayLevelsLbl);
                pane.add(grayLevelsCb);

                final boolean ok = DialogFactory.showOkCancel(captureFrame, translate("capture"), pane, true, () -> {
                    final String tick = tickTextField.getText();
                    if (tick.isEmpty()) {
                        return translate("tick.msg1");
                    }
                    try {
                        if (Integer.parseInt(tick) < 50) {
                            return translate("tick.msg2");
                        }
                    } catch (NumberFormatException ex) {
                        return translate("tick.msg2");
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

        configure.putValue(Action.NAME, margin(translate("capture")));
        configure.putValue(Action.SHORT_DESCRIPTION, translate("capture.settings"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.CAPTURE_SETTINGS));
        return configure;
    }

    /**
     * Should not block (!)
     */
    private void sendCaptureConfiguration(final CaptureEngineConfiguration captureEngineConfiguration) {
        // Ok as very few of that (!)
        new Thread(() -> network.sendCaptureConfiguration(captureEngineConfiguration), "CaptureEngineSettingsSender").start();
    }

    private Action createCompressionConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame compressionFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

                final JPanel pane = new JPanel();
                pane.setLayout(new GridLayout(4, 2, 10, 10));

                final JLabel methodLbl = new JLabel(translate("compression.method"));
                // testing only: final JComboBox<CompressionMethod> methodCb = new JComboBox<>(CompressionMethod.values());
                final JComboBox<CompressionMethod> methodCb = new JComboBox<>(Stream.of(CompressionMethod.values()).filter(e -> !e.equals(CompressionMethod.NONE)).toArray(CompressionMethod[]::new));
                methodCb.setSelectedItem(compressorEngineConfiguration.getMethod());
                pane.add(methodLbl);
                pane.add(methodCb);

                final JLabel useCacheLbl = new JLabel(translate("compression.cache.usage"));
                final JCheckBox useCacheCb = new JCheckBox();
                useCacheCb.setSelected(compressorEngineConfiguration.useCache());
                pane.add(useCacheLbl);
                pane.add(useCacheCb);

                final JLabel maxSizeLbl = new JLabel(translate("compression.cache.max"));
                maxSizeLbl.setToolTipText(translate("compression.cache.max.tooltip"));
                final JTextField maxSizeTf = new JTextField(String.valueOf(compressorEngineConfiguration.getCacheMaxSize()));
                pane.add(maxSizeLbl);
                pane.add(maxSizeTf);

                final JLabel purgeSizeLbl = new JLabel(translate("compression.cache.purge"));
                purgeSizeLbl.setToolTipText(translate("compression.cache.purge.tooltip"));
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

                final boolean ok = DialogFactory.showOkCancel(compressionFrame, translate("compression"), pane, true, () -> {
                    final String max = maxSizeTf.getText();
                    if (max.isEmpty()) {
                        return translate("compression.cache.max.msg1");
                    }
                    final int maxValue;
                    try {
                        maxValue = Integer.parseInt(max);
                    } catch (NumberFormatException ex) {
                        return translate("compression.cache.max.msg2");
                    }
                    if (maxValue <= 0) {
                        return translate("compression.cache.max.msg3");
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

        configure.putValue(Action.NAME, margin(translate("compression")));
        configure.putValue(Action.SHORT_DESCRIPTION, translate("compression.settings"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.COMPRESSION_SETTINGS));
        return configure;
    }

    private String validatePurgeValue(JTextField purgeSizeTf, int maxValue) {
        final String purge = purgeSizeTf.getText();
        if (purge.isEmpty()) {
            return translate("compression.cache.purge.msg1");
        }
        final int purgeValue;
        try {
            purgeValue = Integer.parseInt(purge);
        } catch (NumberFormatException ex) {
            return translate("compression.cache.purge.msg2");
        }
        if (purgeValue <= 0) {
            return translate("compression.cache.purge.msg3");
        }
        if (purgeValue >= maxValue) {
            return translate("compression.cache.purge.msg4");
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
        configure.putValue(Action.SHORT_DESCRIPTION, translate("capture.reset"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.RESET_CAPTURE));
        return configure;
    }

    private Action createToggleFixScreenAction() {
        final Action fitScreen = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                fitToScreenActivated.set(!fitToScreenActivated.get());
                if (!fitToScreenActivated.get()) {
                    frame.resetFactors();
                } else {
                    frame.resetCanvas();
                }
                frame.repaint();
            }
        };

        fitScreen.putValue(Action.NAME, "toggleScreenMode");
        fitScreen.putValue(Action.SHORT_DESCRIPTION, translate("toggle.screen.mode"));
        fitScreen.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.FIT));
        return fitScreen;
    }

    private Action createSettingsAction() {
        final Action settings = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final JPopupMenu choices = new JPopupMenu();
                choices.add(actions.getCaptureEngineConfigurationAction());
                choices.add(actions.getCompressionEngineConfigurationAction());
                choices.add(actions.getNetworkConfigurationAction());
                choices.add(createLookAndFeelSubmenu());
                final Point where = MouseInfo.getPointerInfo().getLocation();
                final JComponent caller = (JComponent) ev.getSource();
                SwingUtilities.convertPointFromScreen(where, caller);
                choices.show(caller, 0, caller.getHeight());
            }
        };

        settings.putValue(Action.NAME, translate("settings"));
        settings.putValue(Action.SHORT_DESCRIPTION, translate("settings"));
        settings.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.SETTINGS));
        return settings;
    }

    private Action createTokenAction() {

        final Action tokenAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final JButton button = (JButton) ev.getSource();

                if (token == null) {
                    final Cursor cursor = frame.getCursor();
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {
                        final URL url = new URL(format(TOKEN_SERVER_URL, networkConfiguration.getPort()));
                        try (final BufferedReader lines = new BufferedReader(new InputStreamReader(url.openStream()))) {
                            token = lines.readLine();
                        }
                    } catch (IOException ex) {
                        Log.error("Could not obtain token", ex);
                        JOptionPane.showMessageDialog(frame, translate("token.create.error.msg"), translate("token"),
                                JOptionPane.ERROR_MESSAGE);
                    } finally {
                        frame.setCursor(cursor);
                    }
                    if (token != null) {
                        button.setText(format(" %s ", token));
                        button.setFont(new Font("Sans Serif", Font.PLAIN, 18));
                        button.setToolTipText(translate("token.copy.msg"));
                    }
                } else {
                    final StringSelection value = new StringSelection(token);
                    final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(value, value);
                }
            }
        };

        tokenAction.putValue(Action.NAME, "createToken");
        tokenAction.putValue(Action.SHORT_DESCRIPTION, translate("token.create.msg"));
        tokenAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.KEY));
        return tokenAction;
    }

    private JMenu createLookAndFeelSubmenu() {
        JMenu submenu = new JMenu(margin(translate("lnf")));
        submenu.setIcon(getOrCreateIcon(ImageNames.LNF));
        submenu.setToolTipText(translate("lnf.switch"));

        final LookAndFeel current = UIManager.getLookAndFeel();
        submenu.add(new JMenuItem(current.getName()));
        submenu.addSeparator();

        for (final UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if (info.getName().equals(current.getName())) {
                continue;
            }
            final JMenuItem mi = new JMenuItem(info.getName());
            mi.addActionListener(ev1 -> switchLookAndFeel(info));
            mi.setText(info.getName());
            submenu.add(mi);
        }
        return submenu;
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

    private String margin(String in) {
        return " " + in;
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
                // set to capture.getWidth()/getHeight() to visualize changed tiles only
                prevWidth = image.getKey().getWidth();
                prevHeight = image.getKey().getHeight();
            }
            if (fitToScreenActivated.get()) {
                if (frame.getCanvas() == null) {
                    frame.computeScaleFactors(prevWidth, prevHeight);
                }
                frame.onCaptureUpdated(scaleImage(image.getKey(), frame.getCanvas().width, frame.getCanvas().height));
            } else {
                frame.onCaptureUpdated(image.getKey());
            }
            receivedTileCounter.add(capture.getDirtyTileCount(), cacheHits);
            skippedTileCounter.add(capture.getSkipped());
            mergedTileCounter.add(capture.getMerged());
            captureCompressionCounter.add(capture.getDirtyTileCount(), compressionRatio);
        }

        private BufferedImage scaleImage(BufferedImage image, int width, int height) {
            AffineTransform scaleTransform = AffineTransform.getScaleInstance(frame.getxFactor(), frame.getyFactor());
            try {
                AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);
                return bilinearScaleOp.filter(image, new BufferedImage(abs(width), abs(height), image.getType()));
            } catch (ImagingOpException e) {
                Log.error(e.getMessage());
                return image;
            }
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
            frame.resetCanvas();
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
        public void onResizeScreen(int width, int height) {
            frame.computeScaleFactors(width, height);
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
