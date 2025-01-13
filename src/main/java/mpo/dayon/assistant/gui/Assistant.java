package mpo.dayon.assistant.gui;

import com.dosse.upnp.UPnP;
import mpo.dayon.assistant.control.ControlEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngineListener;
import mpo.dayon.assistant.network.NetworkAssistantEngineConfiguration;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.network.NetworkAssistantEngineListener;
import mpo.dayon.assistant.utils.NetworkUtilities;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.monitoring.counter.*;
import mpo.dayon.common.network.ClipboardDispatcher;
import mpo.dayon.common.network.message.NetworkMouseLocationMessageHandler;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.utils.Language;
import mpo.dayon.common.version.Version;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static javax.swing.SwingConstants.HORIZONTAL;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class Assistant implements ClipboardOwner {

    private static final String PORT_PARAM = "?port=%s";
    private static final String WHATSMYIP_SERVER_URL = "https://fensterkitt.ch/dayon/whatismyip.php";
    private String tokenServerUrl;

    private final NetworkAssistantEngine networkEngine;

    private BitCounter receivedBitCounter;

    private TileCounter receivedTileCounter;

    private SkippedTileCounter skippedTileCounter;

    private MergedTileCounter mergedTileCounter;

    private CaptureCompressionCounter captureCompressionCounter;

    private ArrayList<Counter<?>> counters;

    private AssistantFrame frame;

    private AssistantConfiguration configuration;

    private NetworkAssistantEngineConfiguration networkConfiguration;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private CompressorEngineConfiguration compressorEngineConfiguration;

    private final Object prevBufferLOCK = new Object();

    private final Object upnpEnabledLOCK = new Object();

    private byte[] prevBuffer = null;

    private int prevWidth = -1;

    private int prevHeight = -1;

    private String token;

    private Boolean upnpEnabled;

    private String publicIp;

    private final AtomicBoolean compatibilityModeActive = new AtomicBoolean(false);

    public Assistant(String tokenServerUrl, String language) {
        networkConfiguration = new NetworkAssistantEngineConfiguration();
        updateTokenServerUrl(tokenServerUrl);

        this.configuration = new AssistantConfiguration();
        // has not been overridden by command line
        if (language == null && !Locale.getDefault().getLanguage().equals(configuration.getLanguage())) {
            Locale.setDefault(Locale.forLanguageTag(configuration.getLanguage()));
        }

        initUpnp();

        DeCompressorEngine decompressor = new DeCompressorEngine(new MyDeCompressorEngineListener());
        decompressor.start(8);

        NetworkMouseLocationMessageHandler mouseHandler = mouse -> frame.onMouseLocationUpdated(mouse.getX(), mouse.getY());
        networkEngine = new NetworkAssistantEngine(decompressor, mouseHandler, this);
        networkEngine.configure(networkConfiguration);
        networkEngine.addListener(new MyNetworkAssistantEngineListener());

        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();

        final String lnf = getDefaultLookAndFeel();
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception ex) {
            Log.warn("Could not set the [" + lnf + "] L&F!", ex);
        }
        initGui();
    }

    private void updateTokenServerUrl(String tokenServerUrl) {
        if (tokenServerUrl != null && !tokenServerUrl.trim().isEmpty()) {
            this.tokenServerUrl = tokenServerUrl + PORT_PARAM;
        } else if (!networkConfiguration.getTokenServerUrl().isEmpty()) {
            this.tokenServerUrl = networkConfiguration.getTokenServerUrl() + PORT_PARAM;
        } else {
            this.tokenServerUrl = DEFAULT_TOKEN_SERVER_URL + PORT_PARAM;
        }

        if (!this.tokenServerUrl.startsWith(DEFAULT_TOKEN_SERVER_URL)) {
            System.setProperty("dayon.custom.tokenServer", this.tokenServerUrl.substring(0, this.tokenServerUrl.indexOf('?')));
        } else {
            System.clearProperty("dayon.custom.tokenServer");
        }
    }

    private void initGui() {
        createCounters();
        if (frame != null) {
            frame.dispose();
        }
        frame = new AssistantFrame(createAssistantActions(), counters, createLanguageSelection(), compatibilityModeActive.get(), networkEngine, isUpnpEnabled());
        FatalErrorHandler.attachFrame(frame);
        frame.addListener(new ControlEngine(networkEngine));
        frame.setVisible(true);
    }

    private void createCounters() {
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
        counters = new ArrayList<>(Arrays.asList(receivedBitCounter, receivedTileCounter, skippedTileCounter, mergedTileCounter, captureCompressionCounter));
    }

    private AssistantActions createAssistantActions() {
        AssistantActions assistantActions = new AssistantActions();
        assistantActions.setIpAddressAction(createWhatIsMyIpAction());
        assistantActions.setCaptureEngineConfigurationAction(createCaptureConfigurationAction());
        assistantActions.setCompressionEngineConfigurationAction(createCompressionConfigurationAction());
        assistantActions.setResetAction(createResetAction());
        assistantActions.setTokenAction(createTokenAction());
        assistantActions.setRemoteClipboardRequestAction(createRemoteClipboardRequestAction());
        assistantActions.setRemoteClipboardSetAction(createRemoteClipboardUpdateAction());
        assistantActions.setScreenshotRequestAction(createScreenshotRequestAction());
        assistantActions.setStartAction(createStartAction());
        assistantActions.setStopAction(createStopAction());
        assistantActions.setToggleCompatibilityModeAction(createToggleCompatibilityModeAction());
        return assistantActions;
    }

    public void clearToken() {
        token = null;
        JButton button = (JButton) frame.getActions().getTokenAction().getValue("button");
        if (button != null) {
            button.setText("");
            button.setToolTipText(translate("token.create.msg"));
        }
    }

    private void stopNetwork() {
        frame.hideSpinner();
        networkEngine.cancel();
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        Log.debug("Lost clipboard ownership");
    }

    private Action createWhatIsMyIpAction() {
        final Action ip = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final JButton button = (JButton) ev.getSource();
                final JPopupMenu choices = new JPopupMenu();

                final JMenuItem publicIpItem = new JMenuItem(translate("ipAddressPublic", publicIp));
                publicIpItem.addActionListener(ev15 -> button.setText(publicIp));
                choices.add(publicIpItem);

                if (publicIp == null) {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            resolvePublicIp();
                        } catch (IOException | InterruptedException ex) {
                            Log.error("Could not determine public IP", ex);
                            JOptionPane.showMessageDialog(frame, translate("ipAddress.msg2"), translate("ipAddress"), JOptionPane.ERROR_MESSAGE);
                            if (ex instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return publicIp;
                    }).thenAcceptAsync(ip -> {
                        if (ip != null) {
                            button.setText(ip);
                            publicIpItem.setText(translate("ipAddressPublic", ip));
                        }
                    });
                }

                NetworkUtilities.getInetAddresses().stream().map(JMenuItem::new).forEach(item -> {
                    item.addActionListener(ev14 -> button.setText(item.getText()));
                    choices.add(item);
                });

                choices.addSeparator();
                choices.add(getJMenuItemCopyIpAndPort(button));

                // -- display the menu
                // ---------------------------------------------------------------------------------

                final Point where = MouseInfo.getPointerInfo().getLocation();

                SwingUtilities.convertPointFromScreen(where, frame);
                choices.show(frame, where.x, where.y);
                final Point frameLocation = frame.getLocationOnScreen();
                final Point toolbarLocation = frame.getToolBar().getLocationOnScreen();
                choices.setLocation(frameLocation.x + 20, toolbarLocation.y + frame.getToolBar().getHeight());
            }
        };
        ip.putValue("DISPLAY_NAME", publicIp); // always a selection
        // ...
        ip.putValue(Action.SHORT_DESCRIPTION, translate("ipAddress.msg1"));
        ip.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.NETWORK_ADDRESS));
        return ip;
    }

    private void resolvePublicIp() throws IOException, InterruptedException {
        // HttpClient doesn't implement AutoCloseable nor close before Java 21!
        @java.lang.SuppressWarnings("squid:S2095")
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WHATSMYIP_SERVER_URL))
                .timeout(Duration.ofSeconds(5))
                .build();
        publicIp = client.send(request, HttpResponse.BodyHandlers.ofString()).body().trim();
    }

    private JMenuItem getJMenuItemCopyIpAndPort(JButton button) {
        final JMenuItem menuItem = new JMenuItem(translate("copy.msg"));
        menuItem.addActionListener(ev12 -> {
            final String url = button.getText() + " " + networkConfiguration.getPort();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), this);
        });
        return menuItem;
    }

    private Action createRemoteClipboardRequestAction() {
        final Action getRemoteClipboard = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                sendRemoteClipboardRequest();
            }
        };
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
        setRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, translate("clipboard.setRemote"));
        setRemoteClipboard.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.UP));
        return setRemoteClipboard;
    }

    private void sendLocalClipboard() {
        ClipboardDispatcher.sendClipboard(networkEngine, frame, this);
    }

    /**
     * Should not block (!)
     */
    private void sendRemoteClipboardRequest() {
        Log.info("Requesting remote clipboard");
        frame.onClipboardRequested();
        // Ok as very few of that (!)
        new Thread(networkEngine::sendRemoteClipboardRequest, "RemoteClipboardRequest").start();
    }

    private Action createCaptureConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame captureFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

                final JPanel pane = new JPanel();
                pane.setLayout(new GridLayout(3, 2, 10, 10));

                final JLabel tickLbl = new JLabel(translate("tick"));
                tickLbl.setToolTipText(translate("tick.tooltip"));
                final JSlider tickMillisSlider = new JSlider(HORIZONTAL, 50, 1000, captureEngineConfiguration.getCaptureTick());
                final Properties tickLabelTable = new Properties(3);
                JLabel actualTick = new JLabel(format("  %dms  ", tickMillisSlider.getValue()));
                tickLabelTable.put(50, new JLabel(translate("min")));
                tickLabelTable.put(550, actualTick);
                tickLabelTable.put(1000, new JLabel(translate("max")));
                tickMillisSlider.setLabelTable(tickLabelTable);
                tickMillisSlider.setMajorTickSpacing(50);
                tickMillisSlider.setPaintTicks(true);
                tickMillisSlider.setPaintLabels(true);
                pane.add(tickLbl);
                pane.add(tickMillisSlider);

                final JLabel grayLevelsLbl = new JLabel(translate("grays"));
                final JSlider grayLevelsSlider = new JSlider(HORIZONTAL, 0, 6, 6 - captureEngineConfiguration.getCaptureQuantization().ordinal());
                final Properties grayLabelTable = new Properties(3);
                JLabel actualLevels = new JLabel(format("  %d  ", toGrayLevel(grayLevelsSlider.getValue()).getLevels()));
                if (!networkConfiguration.isMonochromePeer()) {
                    grayLabelTable.put(0, new JLabel(translate("min")));
                    grayLabelTable.put(3, actualLevels);
                } else {
                    grayLabelTable.put(3, new JLabel(translate("min")));
                    grayLevelsSlider.setMinimum(3);
                }
                grayLabelTable.put(6, new JLabel(translate("max")));
                grayLevelsSlider.setLabelTable(grayLabelTable);
                grayLevelsSlider.setMajorTickSpacing(1);
                grayLevelsSlider.setPaintTicks(true);
                grayLevelsSlider.setPaintLabels(true);
                grayLevelsSlider.setSnapToTicks(true);
                pane.add(grayLevelsLbl).setEnabled(!captureEngineConfiguration.isCaptureColors());
                pane.add(grayLevelsSlider).setEnabled(!captureEngineConfiguration.isCaptureColors());

                final JLabel colorsLbl = new JLabel(translate("colors"));
                final JCheckBox colorsCb = new JCheckBox();
                colorsCb.setSelected(captureEngineConfiguration.isCaptureColors());
                pane.add(colorsLbl).setEnabled(!networkConfiguration.isMonochromePeer());
                pane.add(colorsCb).setEnabled(!networkConfiguration.isMonochromePeer());

                tickMillisSlider.addChangeListener(e -> {
                    actualTick.setText(tickMillisSlider.getValue() < 1000 ? format("%dms", tickMillisSlider.getValue()) : "1s");
                    if (!tickMillisSlider.getValueIsAdjusting()) {
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                                toGrayLevel(grayLevelsSlider.getValue()), captureEngineConfiguration.isCaptureColors()));
                    }
                });
                grayLevelsSlider.addChangeListener(e -> {
                    actualLevels.setText(format("%d", toGrayLevel(grayLevelsSlider.getValue()).getLevels()));
                    if (!grayLevelsSlider.getValueIsAdjusting() && !captureEngineConfiguration.isCaptureColors()) {
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                                toGrayLevel(grayLevelsSlider.getValue()), false));
                    }
                });
                colorsCb.addActionListener(e -> {
                    grayLevelsLbl.setEnabled(!colorsCb.isSelected());
                    grayLevelsSlider.setEnabled(!colorsCb.isSelected());
                });

                final boolean ok = DialogFactory.showOkCancel(captureFrame, translate("capture"), pane, true, null);

                if (ok) {
                    final CaptureEngineConfiguration newCaptureEngineConfiguration = new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                            toGrayLevel(grayLevelsSlider.getValue()), colorsCb.isSelected());
                    updateCaptureConfiguration(newCaptureEngineConfiguration);
                }
            }
        };
        configure.putValue(Action.SHORT_DESCRIPTION, translate("capture.settings"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.CAPTURE_SETTINGS));
        return configure;
    }

    private void updateCaptureConfiguration(CaptureEngineConfiguration newCaptureEngineConfiguration) {
        if (!newCaptureEngineConfiguration.equals(captureEngineConfiguration)) {
            captureEngineConfiguration = newCaptureEngineConfiguration;
            captureEngineConfiguration.persist();
            sendCaptureConfiguration(captureEngineConfiguration);
        }
    }

    private static Gray8Bits toGrayLevel(int value) {
        return Gray8Bits.values()[6 - value];
    }

    /**
     * Should not block (!)
     */
    private void sendCaptureConfiguration(final CaptureEngineConfiguration captureEngineConfiguration) {
        // Ok as very few of that (!)
        new Thread(() -> networkEngine.sendCaptureConfiguration(captureEngineConfiguration), "CaptureEngineSettingsSender").start();
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
                final JTextField maxSizeTf = new JTextField(valueOf(compressorEngineConfiguration.getCacheMaxSize()));
                pane.add(maxSizeLbl);
                pane.add(maxSizeTf);

                final JLabel purgeSizeLbl = new JLabel(translate("compression.cache.purge"));
                purgeSizeLbl.setToolTipText(translate("compression.cache.purge.tooltip"));
                final JTextField purgeSizeTf = new JTextField(valueOf(compressorEngineConfiguration.getCachePurgeSize()));
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
        configure.putValue(Action.SHORT_DESCRIPTION, translate("compression.settings"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.COMPRESSION_SETTINGS));
        return configure;
    }

    private static String validatePurgeValue(JTextField purgeSizeTf, int maxValue) {
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
        new Thread(() -> networkEngine.sendCompressorConfiguration(compressorEngineConfiguration), "CompressorEngineSettingsSender").start();
    }

    private Action createResetAction() {
        final Action configure = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                // Currently making a RESET within the assisted ...
                sendCaptureConfiguration(captureEngineConfiguration);
            }
        };
        configure.putValue(Action.SHORT_DESCRIPTION, translate("capture.reset"));
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.RESET_CAPTURE));
        return configure;
    }

    private Action createTokenAction() {

        final Action tokenAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final JButton button = (JButton) ev.getSource();
                this.putValue("button", button);

                if (token == null) {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            requestToken();
                        } catch (IOException | InterruptedException ex) {
                            Log.error("Could not obtain token", ex);
                            JOptionPane.showMessageDialog(frame, translate("token.create.error.msg"), translate("connection.settings.token"), JOptionPane.ERROR_MESSAGE);
                            if (ex instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return token;
                    }).thenAcceptAsync(tokenString -> {
                        if (tokenString  != null) {
                            token = tokenString;
                            button.setText(format(" %s", tokenString));
                            button.setToolTipText(translate("token.copy.msg"));
                        }
                    });
                }
                final StringSelection value = new StringSelection(token);
                final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(value, value);
            }

            private void requestToken() throws IOException, InterruptedException {
                Log.debug("Requesting token using: " + tokenServerUrl);
                // HttpClient doesn't implement AutoCloseable nor close before Java 21!
                @java.lang.SuppressWarnings("squid:S2095")
                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(format(tokenServerUrl, networkConfiguration.getPort())))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                token = client.send(request, HttpResponse.BodyHandlers.ofString()).body().trim();
            }
        };
        tokenAction.putValue("token", token);
        tokenAction.putValue(Action.SHORT_DESCRIPTION, translate("token.create.msg"));
        tokenAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.KEY));
        return tokenAction;
    }

    private Action createStartAction() {
        final Action startAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                new Assistant.NetWorker().execute();
            }
        };
        startAction.setEnabled(false);
        startAction.putValue(Action.SHORT_DESCRIPTION, translate("start.session"));
        startAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.START));
        return startAction;
    }

    private Action createStopAction() {
        final Action stopAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                stopNetwork();
            }
        };
        stopAction.setEnabled(false);
        stopAction.putValue(Action.SHORT_DESCRIPTION, translate("stop.session"));
        stopAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.STOP));
        return stopAction;
    }

    private Action createToggleCompatibilityModeAction() {
        final Action compatibilityMode = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                compatibilityModeActive.set(!compatibilityModeActive.get());
                if (compatibilityModeActive.get()) {
                    JOptionPane.showMessageDialog(frame, translate("compatibility.mode.info"),
                            translate("compatibility.mode.active"), JOptionPane.WARNING_MESSAGE);
                }
                frame.repaint();
            }
        };
        compatibilityMode.putValue(Action.SHORT_DESCRIPTION, translate("compatibility.mode"));
        compatibilityMode.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.COMPATIBILITY));
        return compatibilityMode;
    }

    private JComboBox<Language> createLanguageSelection() {
        final JComboBox<Language> languageSelection = new JComboBox<>(Language.values());
        languageSelection.setMaximumRowCount(languageSelection.getItemCount());
        languageSelection.setBorder(BorderFactory.createEmptyBorder(7, 3, 6, 2));
        languageSelection.setFocusable(false);
        languageSelection.setToolTipText(translate("changeLanguage"));
        languageSelection.setSelectedItem(Arrays.stream(Language.values()).filter(e -> e.getShortName().equals(Locale.getDefault().getLanguage())).findFirst().orElse(Language.EN));
        languageSelection.setRenderer(new LanguageRenderer());
        languageSelection.addActionListener(ev -> {
                Locale.setDefault(Locale.forLanguageTag(valueOf(languageSelection.getSelectedItem())));
                Log.info(format("New language %s", Locale.getDefault().getLanguage()));
                configuration = new AssistantConfiguration(Locale.getDefault().getLanguage());
                configuration.persist();
                initGui();
            }
        );
        return languageSelection;
    }

    private static class LanguageRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, ((Language) value).getName(), index, isSelected, cellHasFocus);
            return this;
        }
    }

    private Action createScreenshotRequestAction() {
        final Action screenshotAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                new Thread(networkEngine::sendScreenshotRequest, "ScreenshotRequest").start();
            }
        };
        screenshotAction.setEnabled(false);
        screenshotAction.putValue(Action.SHORT_DESCRIPTION, translate("send.prtScrKey"));
        screenshotAction.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.CAM));
        return screenshotAction;
    }

    private class NetWorker extends SwingWorker<String, String> {
        @Override
        protected String doInBackground() {
            if (!isCancelled()) {
                startNetwork();
            }
            return null;
        }

        private void startNetwork() {
            frame.onGettingReady();
            if (publicIp == null) {
                try {
                    resolvePublicIp();
                } catch (IOException | InterruptedException ex) {
                    Log.error("Could not determine public IP", ex);
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (!networkEngine.selfTest(publicIp)) {
                JOptionPane.showMessageDialog(frame, translate("port.error.msg1", networkConfiguration.getPort()), translate("port.error"), JOptionPane.WARNING_MESSAGE);
            }
            networkEngine.start(compatibilityModeActive.get());
        }

        @Override
        protected void done() {
            try {
                if (!isCancelled()) {
                    super.get();
                    Log.debug("NetWorker is done");
                }
            } catch (InterruptedException | ExecutionException ie) {
                Log.info("NetWorker was cancelled");
                Thread.currentThread().interrupt();
            }
        }
    }

    public CompletableFuture<Boolean> isUpnpEnabled() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (upnpEnabledLOCK) {
                while (upnpEnabled == null) {
                    try {
                        upnpEnabledLOCK.wait(5000);
                    } catch (InterruptedException e) {
                        Log.warn("Swallowed", e);
                        Thread.currentThread().interrupt();
                    }
                }
                return upnpEnabled;
            }
        });
    }

    private void initUpnp() {
        synchronized (upnpEnabledLOCK) {
            CompletableFuture.supplyAsync(UPnP::isUPnPAvailable).thenApply(enabled -> {
                Log.info(format("UPnP is %s", enabled.booleanValue() ? "enabled" : "disabled"));
                upnpEnabled = enabled;
                return enabled;
            });
            upnpEnabledLOCK.notifyAll();
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
            if (frame.getFitToScreenActivated()) {
                if (frame.getCanvas() == null) {
                    frame.computeScaleFactors(prevWidth, prevHeight);
                }
                // required as the canvas might have been reset if keepAspectRatio caused a resizing of the window
                final Dimension canvasDimension = frame.getCanvas();
                if (canvasDimension != null) {
                    frame.onCaptureUpdated(scaleImage(image.getKey(), canvasDimension.width, canvasDimension.height));
                }
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
                return bilinearScaleOp.filter(image, new BufferedImage(abs(width), abs(height), image.getType() == 0 ? TYPE_INT_ARGB_PRE : TYPE_BYTE_GRAY));
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
        public void onConnected(Socket connection, char osId, String inputLocale, int peerMajorVersion) {
            assureCompatibility(peerMajorVersion);
            sendCaptureConfiguration(captureEngineConfiguration);
            sendCompressorConfiguration(compressorEngineConfiguration);
            frame.resetCanvas();
            frame.onSessionStarted(osId, inputLocale, peerMajorVersion);
        }

        private void assureCompatibility(int peerMajorVersion) {
            if (!Version.isColoredVersion(peerMajorVersion) && (captureEngineConfiguration.isCaptureColors() || captureEngineConfiguration.getCaptureQuantization().getLevels() < Gray8Bits.X_32.getLevels())) {
                Log.warn(format("Pre color v%d.x.x peer detected: CaptureEngineConfiguration adjusted to %s", peerMajorVersion, Gray8Bits.X_128));
                captureEngineConfiguration = new CaptureEngineConfiguration(captureEngineConfiguration.getCaptureTick(), Gray8Bits.X_128, false);
                captureEngineConfiguration.persist();
            }
        }

        @Override
        public void onFingerprinted(String fingerprints) {
            if (null == fingerprints) {
                fingerprints = translate("compatibility.mode.enable");
            }
            frame.setFingerprints(fingerprints);
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
            networkConfiguration.setMonochromePeer(false);
        }

        @Override
        public void onTerminating() {
            Log.info("Session got terminated by peer");
            frame.onTerminating();
            networkConfiguration.setMonochromePeer(false);
        }

        @Override
        public void onIOError(IOException error) {
            frame.onIOError(error);
            networkConfiguration.setMonochromePeer(false);
        }

        @Override
        public void onReconfigured(NetworkAssistantEngineConfiguration networkEngineConfiguration) {
            networkConfiguration = networkEngineConfiguration;
            updateTokenServerUrl(networkConfiguration.getTokenServerUrl());
            clearToken();
        }
    }
}
