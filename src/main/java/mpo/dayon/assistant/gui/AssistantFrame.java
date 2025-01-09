package mpo.dayon.assistant.gui;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.monitoring.counter.Counter;
import mpo.dayon.common.utils.Language;
import mpo.dayon.common.version.Version;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.awt.event.KeyEvent.*;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

class AssistantFrame extends BaseFrame {

    private static final int OFFSET = 6;

    private static final int DEFAULT_FACTOR = 1;
    
    private static final char EMPTY_CHAR = ' ';

    private final transient Listeners<AssistantFrameListener> listeners = new Listeners<>();

    private final JScrollPane assistantPanelWrapper;

    private final AssistantPanel assistantPanel;

    private final transient AssistantActions actions;

    private Timer sessionTimer;

    private JComponent center;

    private final JToggleButton controlToggleButton;

    private final JToggleButton compatibilityToggleButton;

    private final JToggleButton windowsKeyToggleButton;

    private final JToggleButton ctrlKeyToggleButton;

    private final JToggleButton fitToScreenToggleButton;

    private final JToggleButton keepAspectRatioToggleButton;

    private final JButton startButton;

    private final JButton stopButton;

    private final JButton screenshotButton;

    private final JButton tokenButton;

    private final AtomicBoolean controlActivated = new AtomicBoolean(false);

    private final AtomicBoolean windowsKeyActivated = new AtomicBoolean(false);

    private final AtomicBoolean ctrlKeyActivated = new AtomicBoolean(false);

    private final AtomicBoolean fitToScreenActivated = new AtomicBoolean(false);

    private final AtomicBoolean keepAspectRatioActivated = new AtomicBoolean(false);

    private final AtomicBoolean isImmutableWindowsSize = new AtomicBoolean(false);

    private double xFactor = DEFAULT_FACTOR;

    private double yFactor = DEFAULT_FACTOR;

    private Dimension canvas;

    private JTabbedPane tabbedPane;

    private final JComboBox<Language> languageSelection;

    private char osId;

    AssistantFrame(AssistantActions actions, ArrayList<Counter<?>> counters, JComboBox<Language> languageSelection, boolean compatibilityModeActive, NetworkAssistantEngine networkEngine, CompletableFuture<Boolean> isUpnpEnabled) {
        RepeatingReleasedEventsFixer.install();
        super.setFrameType(FrameType.ASSISTANT);
        this.actions = actions;
        this.actions.setNetworkConfigurationAction(createAssistantConnectionSettingsAction(isUpnpEnabled, networkEngine));
        this.startButton = createButton(actions.getStartAction());
        this.stopButton = createButton(actions.getStopAction(), false);
        this.tokenButton = createTokenButton(actions.getTokenAction());
        this.compatibilityToggleButton = createToggleButton(actions.getToggleCompatibilityModeAction(), true, compatibilityModeActive);
        this.controlToggleButton = createToggleButton(createToggleControlMode());
        this.fitToScreenToggleButton = createToggleButton(createToggleFixScreenAction());
        this.keepAspectRatioToggleButton = createToggleButton(createToggleKeepAspectRatioAction(), false);
        this.windowsKeyToggleButton = createToggleButton(createSendWindowsKeyAction());
        this.ctrlKeyToggleButton = createToggleButton(createSendCtrlKeyAction());
        this.screenshotButton = createButton(actions.getScreenshotRequestAction());
        this.languageSelection = languageSelection;
        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar(counters));
        assistantPanel = new AssistantPanel();
        assistantPanel.setFocusable(false);
        assistantPanelWrapper = new JScrollPane(assistantPanel);
        // -------------------------------------------------------------------------------------------------------------
        // Not really needed for the time being - allows for seeing the TAB with a regular KEY listener ...
        setFocusTraversalKeysEnabled(false);
        addListeners();
        // the network has been before we've been registered as a listener ...
        onReady();
    }

    public AssistantActions getActions() {
        return actions;
    }

    private void addListeners() {
        addFocusListener();
        addKeyListeners();
        addMouseListeners();
        addResizeListener();
        addMinMaximizedListener();
    }

    Dimension getCanvas() {
        return canvas;
    }

    double getxFactor() {
        return xFactor;
    }

    double getyFactor() {
        return yFactor;
    }

    boolean getFitToScreenActivated() {
        return fitToScreenActivated.get();
    }
    boolean getKeepAspectRatioActivated() {
        return keepAspectRatioActivated.get();
    }

    private void addMouseListeners() {
        assistantPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent ev) {
                if (controlActivated.get()) {
                    fireOnMousePressed(ev.getX(), ev.getY(), ev.getButton());
                }
            }

            @Override
            public void mouseReleased(MouseEvent ev) {
                if (controlActivated.get()) {
                    fireOnMouseReleased(ev.getX(), ev.getY(), ev.getButton());
                }
            }
        });

        assistantPanel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent ev) {
                if (controlActivated.get()) {
                    fireOnMouseMove(ev.getX(), ev.getY());
                }
            }

            @Override
            public void mouseMoved(MouseEvent ev) {
                if (controlActivated.get()) {
                    fireOnMouseMove(ev.getX(), ev.getY());
                }
            }
        });

        assistantPanel.addMouseWheelListener(ev -> {
            if (controlActivated.get()) {
                fireOnMouseWheeled(ev.getX(), ev.getY(), ev.getWheelRotation());
            }
        });
    }

    private void addKeyListeners() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ev) {
                if (controlActivated.get()) {
                    fireOnKeyPressed(ev.getKeyCode(), ev.getKeyChar());
                }
            }

            @Override
            public void keyReleased(KeyEvent ev) {
                if (controlActivated.get()) {
                    fireOnKeyReleased(ev.getKeyCode(), ev.getKeyChar());
                }
            }
        });
    }

    private void addResizeListener() {
        addComponentListener(new ComponentAdapter() {
            private Timer resizeTimer;
            @Override
            public void componentResized(ComponentEvent ev) {
                if (resizeTimer != null) {
                    resizeTimer.stop();
                }
                resizeTimer = new Timer(500, e -> resetCanvas());
                resizeTimer.setRepeats(false);
                resizeTimer.start();
            }
        });
    }

    private void addMinMaximizedListener() {
        addWindowStateListener(event -> isImmutableWindowsSize.set((event.getNewState() & Frame.ICONIFIED) == Frame.ICONIFIED || (event.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH));
    }

    private void addFocusListener() {
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent ev) {
                if (controlActivated.get()) {
                    fireOnKeyReleased(-1, Character.MIN_VALUE);
                    if (windowsKeyActivated.get()) {
                        windowsKeyToggleButton.setSelected(false);
                        windowsKeyActivated.set(!windowsKeyActivated.get());
                    }
                    if (ctrlKeyActivated.get()) {
                        ctrlKeyToggleButton.setSelected(false);
                        ctrlKeyActivated.set(!ctrlKeyActivated.get());
                    }
                }
            }
        });
    }

    public void addListener(AssistantFrameListener listener) {
        listeners.add(listener);
    }

    private ToolBar createToolBar() {
        ToolBar toolbar = new ToolBar();
        toolbar.add(createTabbedPane());
        return toolbar;
    }

    private JTabbedPane createTabbedPane() {
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        connectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        connectionPanel.add(startButton);
        connectionPanel.add(stopButton);
        connectionPanel.add(tokenButton);
        connectionPanel.add(createButton(actions.getIpAddressAction()));
        connectionPanel.add(compatibilityToggleButton);

        JPanel sessionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sessionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        sessionPanel.add(fitToScreenToggleButton);
        sessionPanel.add(keepAspectRatioToggleButton);
        sessionPanel.add(controlToggleButton);
        sessionPanel.add(windowsKeyToggleButton);
        sessionPanel.add(ctrlKeyToggleButton);
        sessionPanel.add(screenshotButton);
        sessionPanel.add(createButton(actions.getRemoteClipboardRequestAction()));
        sessionPanel.add(createButton(actions.getRemoteClipboardSetAction()));
        sessionPanel.add(createButton(actions.getResetAction()));

        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        settingsPanel.add(createButton(actions.getNetworkConfigurationAction()));
        settingsPanel.add(createButton(actions.getCaptureEngineConfigurationAction()));
        settingsPanel.add(createButton(actions.getCompressionEngineConfigurationAction()));
        settingsPanel.add(languageSelection);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(translate("connection"), connectionPanel);
        tabbedPane.addTab(translate("session"), sessionPanel);
        tabbedPane.addTab(translate("settings"), settingsPanel);
        // must not be focusable or the key listener won't work
        tabbedPane.setFocusable(false);
        return tabbedPane;
    }

    private static JButton createTokenButton(Action tokenAction) {
        String token = (String) tokenAction.getValue("token");
        JButton button = createButton(tokenAction);
        if (token != null) {
            button.setText(format(" %s", token));
            button.setToolTipText(translate("token.copy.msg"));
        }
        return button;
    }

    private static StatusBar createStatusBar(ArrayList<Counter<?>> counters) {
        final StatusBar statusBar = new StatusBar();
        final Component horizontalStrut = Box.createHorizontalStrut(10);
        statusBar.add(horizontalStrut);
        for (Counter<?> counter : counters) {
            statusBar.addSeparator();
            statusBar.addCounter(counter, counter.getWidth());
        }
        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.addSeparator();
        statusBar.addConnectionDuration();
        statusBar.add(horizontalStrut);
        return statusBar;
    }

    private Action createToggleControlMode() {
        final Action remoteControl = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                controlActivated.set(!controlActivated.get());
                windowsKeyToggleButton.setEnabled(controlActivated.get());
                ctrlKeyToggleButton.setEnabled(controlActivated.get());
            }
        };
        remoteControl.putValue(Action.SHORT_DESCRIPTION, translate("control.mode"));
        remoteControl.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.WATCH));
        remoteControl.putValue(ROLLOVER_ICON, getOrCreateIcon(ImageNames.WATCH));
        remoteControl.putValue(SELECTED_ICON, getOrCreateIcon(ImageNames.CONTROL));
        return remoteControl;
    }

    private Action createSendWindowsKeyAction() {
        final Action sendWindowsKey = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final int virtualKey = osId != 'm' ? VK_WINDOWS : VK_META;
                if (windowsKeyActivated.get()) {
                    fireOnKeyReleased(virtualKey, EMPTY_CHAR);
                } else {
                    fireOnKeyPressed(virtualKey, EMPTY_CHAR);
                }
                windowsKeyActivated.set(!windowsKeyActivated.get());
            }
        };
        sendWindowsKey.putValue(Action.SHORT_DESCRIPTION, translate("send.windowsKey"));
        sendWindowsKey.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.WIN));
        return sendWindowsKey;
    }

    private Action createSendCtrlKeyAction() {
        final Action sendCtrlKey = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                if (ctrlKeyActivated.get()) {
                    fireOnKeyReleased(VK_CONTROL, EMPTY_CHAR);
                } else {
                    fireOnKeyPressed(VK_CONTROL, EMPTY_CHAR);
                }
                ctrlKeyActivated.set(!ctrlKeyActivated.get());
            }
        };
        sendCtrlKey.putValue(Action.SHORT_DESCRIPTION, translate("send.ctrlKey"));
        sendCtrlKey.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.CTRL));
        return sendCtrlKey;
    }

    private Action createToggleFixScreenAction() {
        final Action fitScreen = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                fitToScreenActivated.set(!fitToScreenActivated.get());
                if (!fitToScreenActivated.get()) {
                    keepAspectRatioToggleButton.setVisible(false);
                    resetFactors();
                } else {
                    keepAspectRatioToggleButton.setVisible(true);
                    resetCanvas();
                }
                repaint();
            }
        };
        fitScreen.putValue(Action.SHORT_DESCRIPTION, translate("toggle.screen.mode"));
        fitScreen.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.FULLSCREEN));
        fitScreen.putValue(ROLLOVER_ICON, getOrCreateIcon(ImageNames.FULLSCREEN));
        fitScreen.putValue(SELECTED_ICON, getOrCreateIcon(ImageNames.FIT));
        return fitScreen;
    }

    private Action createToggleKeepAspectRatioAction() {
        final Action keepAspectRatio = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                keepAspectRatioActivated.set(!keepAspectRatioActivated.get());
                resetCanvas();
                repaint();
            }
        };
        keepAspectRatio.putValue(Action.SHORT_DESCRIPTION, translate("toggle.keep.aspect"));
        keepAspectRatio.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.UNLOCKED));
        keepAspectRatio.putValue(ROLLOVER_ICON, getOrCreateIcon(ImageNames.UNLOCKED));
        keepAspectRatio.putValue(SELECTED_ICON, getOrCreateIcon(ImageNames.LOCK));
        return keepAspectRatio;
    }

    void onReady() {
        hideSpinner();
        validate();
        repaint();
        // connection
        actions.getStartAction().setEnabled(true);
        actions.getStopAction().setEnabled(false);
        startButton.setVisible(true);
        stopButton.setVisible(false);
        actions.getTokenAction().setEnabled(true);
        actions.getToggleCompatibilityModeAction().setEnabled(true);
        actions.getIpAddressAction().setEnabled(true);
        // session
        screenshotButton.setEnabled(false);
        actions.getResetAction().setEnabled(false);
        // settings
        actions.getNetworkConfigurationAction().setEnabled(true);
        actions.getCaptureEngineConfigurationAction().setEnabled(true);
        languageSelection.setEnabled(true);
        disableControls();
        clearFingerprints();
        getStatusBar().setMessage(translate("ready"));
    }

    void onHttpStarting(int port) {
        // connection
        startButton.setVisible(false);
        actions.getStopAction().setEnabled(true);
        stopButton.setVisible(true);
        actions.getToggleCompatibilityModeAction().setEnabled(false);
        actions.getIpAddressAction().setEnabled(false);
        // settings
        actions.getNetworkConfigurationAction().setEnabled(false);
        languageSelection.setEnabled(false);
        clearFingerprints();
        getStatusBar().setMessage(translate("listening", port));
    }

    void onGettingReady() {
        actions.getStartAction().setEnabled(false);
        showSpinner();
    }

    private void showSpinner() {
        center = new Spinner();
        add(center, BorderLayout.CENTER);
    }

    boolean onAccepted(Socket connection) {
        if (JOptionPane.showOptionDialog(this, translate("connection.incoming.msg1"),
            translate("connection.incoming", connection.getInetAddress().getHostAddress()), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
            getOrCreateIcon(ImageNames.USERS), okCancelOptions, okCancelOptions[1]) == 0) {
            return false;
        }
        hideSpinner();
        getStatusBar().setMessage(translate("connection.incoming.msg2", connection.getInetAddress().getHostAddress()));
        center = assistantPanelWrapper;
        add(center, BorderLayout.CENTER);
        screenshotButton.setEnabled(true);
        actions.getResetAction().setEnabled(true);
        actions.getTokenAction().setEnabled(false);
        enableControls();
        validate();
        repaint();
        return true;
    }

    void onClipboardRequested() {
        toggleTransferControls(false);
    }

    @Override
    public void onClipboardSending() {
        toggleTransferControls(false);
    }

    @Override
    public void onClipboardSent() {
        toggleTransferControls(true);
    }

    void onClipboardReceived() {
        toggleTransferControls(true);
    }

    void onSessionStarted(char osId, String inputLocale, int assistedMajorVersion) {
        this.osId = osId;
        if (osId == 'm') {
            windowsKeyToggleButton.setIcon(getOrCreateIcon(ImageNames.CMD));
            windowsKeyToggleButton.setToolTipText(translate("send.cmdKey"));
        } else {
            windowsKeyToggleButton.setIcon(getOrCreateIcon(ImageNames.WIN));
            windowsKeyToggleButton.setToolTipText(translate("send.winKey"));
        }
        if (Version.isOutdatedVersion(Version.get().getMajor(), assistedMajorVersion)) {
            String infoMessage = format("%s%n%s", translate("outdated.msg1"), translate("outdated.msg2"));
            JOptionPane.showMessageDialog(this,  infoMessage, "", JOptionPane.INFORMATION_MESSAGE);
        }
        if (!inputLocale.isEmpty() && !inputLocale.equals(InputContext.getInstance().getLocale().toString())) {
            String infoMessage = format("%s%n%s%n%s", translate("keyboardlayout.msg1", inputLocale), translate("keyboardlayout.msg2"), translate("keyboardlayout.msg3"));
            JOptionPane.showMessageDialog(this,  infoMessage, "", JOptionPane.INFORMATION_MESSAGE);
        }
        long sessionStartTime = Instant.now().getEpochSecond();
        sessionTimer = new Timer(1000, e -> {
            final long seconds = Instant.now().getEpochSecond() - sessionStartTime;
            getStatusBar().setSessionDuration(format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
        });
        sessionTimer.start();
        tabbedPane.setSelectedIndex(1);
    }

    void onDisconnecting() {
        stopSessionTimer();
        tabbedPane.setSelectedIndex(0);
    }

    void onTerminating() {
        actions.getStartAction().setEnabled(false);
        actions.getStopAction().setEnabled(false);
        actions.getResetAction().setEnabled(false);
        disableControls();
        stopSessionTimer();
        tabbedPane.setSelectedIndex(0);
    }

    void onIOError(IOException error) {
        onTerminating();
        hideSpinner();
        validate();
        repaint();
        String errorMessage = error.getMessage() != null ? translate("comm.error.msg1", translate(error.getMessage())) : translate("comm.error.msg1", "!");
        JOptionPane.showMessageDialog(this, errorMessage, translate("comm.error"), JOptionPane.ERROR_MESSAGE);
    }

    void computeScaleFactors(int sourceWidth, int sourceHeight) {
        Log.debug(format("ComputeScaleFactors for w: %d h: %d", sourceWidth, sourceHeight));
        canvas = assistantPanelWrapper.getSize();
        canvas.setSize(canvas.getWidth() - OFFSET, canvas.getHeight() - OFFSET);
        xFactor = canvas.getWidth() / sourceWidth;
        yFactor = canvas.getHeight() / sourceHeight;
        if (keepAspectRatioActivated.get() && !isImmutableWindowsSize.get() && abs(xFactor - yFactor) > 0.01) {
            resizeWindow(sourceWidth, sourceHeight);
        }
    }

    private void resizeWindow(int sourceWidth, int sourceHeight) {
        Log.debug("%s", () -> format("Resize  W:H %d:%d x:y %f:%f", this.getWidth(), this.getHeight(), xFactor, yFactor));
        int menuHeight = this.getHeight() - canvas.height;
        final Rectangle maximumWindowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (xFactor < yFactor) {
            if ((sourceWidth * yFactor) + OFFSET < maximumWindowBounds.width) {
                xFactor = yFactor;
                Log.debug("Get wider");
                this.setSize((int) (sourceWidth * xFactor) + OFFSET, this.getHeight());
            } else {
                yFactor = xFactor;
                Log.debug("Get lower");
                this.setSize(this.getWidth(), (int) (sourceHeight * yFactor) + menuHeight + OFFSET);
            }
        } else {
            if ((sourceHeight * xFactor) + menuHeight + OFFSET < maximumWindowBounds.height) {
                yFactor = xFactor;
                Log.debug("Get higher");
                this.setSize(this.getWidth(), (int) (sourceHeight * yFactor) + menuHeight + OFFSET);
            } else {
                xFactor = yFactor;
                Log.debug("Get narrower");
                this.setSize((int) (sourceWidth * xFactor) + OFFSET, this.getHeight());
            }
        }
        Log.debug("%s", () -> format("Resized W:H %d:%d x:y %f:%f", this.getWidth(), this.getHeight(), xFactor, yFactor));
    }

    private void resetFactors() {
        xFactor = DEFAULT_FACTOR;
        yFactor = DEFAULT_FACTOR;
    }

    void resetCanvas() {
        canvas = null;
    }

    private void disableControls() {
        controlActivated.set(false);
        windowsKeyActivated.set(false);
        controlToggleButton.setEnabled(false);
        windowsKeyToggleButton.setEnabled(false);
        ctrlKeyToggleButton.setEnabled(false);
        toggleTransferControls(false);
    }

    private void toggleTransferControls(boolean enabled) {
        actions.getRemoteClipboardSetAction().setEnabled(enabled);
        actions.getRemoteClipboardRequestAction().setEnabled(enabled);
    }

    private void enableControls() {
        controlToggleButton.setSelected(false);
        controlToggleButton.setEnabled(true);
        windowsKeyToggleButton.setSelected(false);
        ctrlKeyToggleButton.setSelected(false);
        toggleTransferControls(true);
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
    }

    void hideSpinner() {
        if (center != null) {
            remove(center);
        }
    }

    void onCaptureUpdated(final BufferedImage captureImage) {
        assistantPanel.onCaptureUpdated(captureImage);
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void onMouseLocationUpdated(int x, int y) {
        int xs = (int) Math.round(x * xFactor);
        int ys = (int) Math.round(y * yFactor);
        assistantPanel.onMouseLocationUpdated(xs, ys);
    }

    private void fireOnMouseMove(int x, int y) {
        listeners.getListeners().forEach(listener -> listener.onMouseMove(scaleXPosition(x), scaleYPosition(y)));
    }

    private void fireOnMousePressed(int x, int y, int button) {
        listeners.getListeners().forEach(listener -> listener.onMousePressed(scaleXPosition(x), scaleYPosition(y), button));
    }

    private void fireOnMouseReleased(int x, int y, int button) {
        listeners.getListeners().forEach(listener -> listener.onMouseReleased(scaleXPosition(x), scaleYPosition(y), button));
    }

    private void fireOnMouseWheeled(int x, int y, int rotations) {
        listeners.getListeners().forEach(listener -> listener.onMouseWheeled(scaleXPosition(x), scaleYPosition(y), rotations));
    }

    private int scaleYPosition(int y) {
        return (int) Math.round(y / yFactor);
    }

    private int scaleXPosition(int x) {
        return (int) Math.round(x / xFactor);
    }

    private void fireOnKeyPressed(int keyCode, char keyChar) {
        listeners.getListeners().forEach(listener -> listener.onKeyPressed(keyCode, keyChar));
    }

    private void fireOnKeyReleased(int keyCode, char keyChar) {
        listeners.getListeners().forEach(listener -> listener.onKeyReleased(keyCode, keyChar));
    }

    private static class Spinner extends JPanel {
        final ImageIcon waiting = getOrCreateIcon(ImageNames.WAITING);

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            final int x = (getWidth() - waiting.getIconWidth()) / 2;
            final int y = (getHeight() - waiting.getIconHeight()) / 2;
            g.drawImage(waiting.getImage(), x, y, this);
        }
    }
}
