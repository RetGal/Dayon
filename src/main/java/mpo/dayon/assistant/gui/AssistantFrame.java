package mpo.dayon.assistant.gui;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.gui.common.*;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.monitoring.counter.Counter;
import mpo.dayon.common.version.Version;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class AssistantFrame extends BaseFrame {

    private final transient Listeners<AssistantFrameListener> listeners = new Listeners<>();

    private final JScrollPane assistantPanelWrapper;

    private final AssistantPanel assistantPanel;

    private final transient Action ipAddressAction;

    private final transient Action networkConfigurationAction;

    private final transient Action captureEngineConfigurationAction;

    private final transient Action compressorEngineConfigurationAction;

    private final transient Action resetAction;

    private final transient Action lookAndFeelAction;

    private final transient Action remoteClipboardRequestAction;

    private final transient Action remoteClipboardSetAction;

    private final transient Action startAction;

    private final transient Action stopAction;

    private final transient Position position;

    private final transient Dimension dimension;

    private transient FrameConfiguration configuration;

    private final static FrameType frameType = FrameType.ASSISTANT;

    private Timer sessionTimer;

    @Nullable
    private JComponent center;

    private final AtomicBoolean controlActivated = new AtomicBoolean(false);

    AssistantFrame(FrameConfiguration configuration, Action ipAddressAction, Action networkConfigurationAction,
                          Action captureEngineConfigurationAction, Action compressorEngineConfigurationAction, Action resetAction, Action lookAndFeelAction,
                          Action remoteClipboardRequestAction, Action remoteClipboardSetAction, AssistantStartAction startAction, AssistantStopAction stopAction, Set<Counter<?>> counters) {
        this.configuration = configuration;

        setTitle("Dayon! (" + Babylon.translate("assistant") + ") " + Version.get());

        this.ipAddressAction = ipAddressAction;
        this.networkConfigurationAction = networkConfigurationAction;
        this.captureEngineConfigurationAction = captureEngineConfigurationAction;
        this.compressorEngineConfigurationAction = compressorEngineConfigurationAction;
        this.resetAction = resetAction;
        this.lookAndFeelAction = lookAndFeelAction;
        this.remoteClipboardRequestAction = remoteClipboardRequestAction;
        this.remoteClipboardSetAction = remoteClipboardSetAction;
        this.startAction = startAction;
        this.stopAction = stopAction;

        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar(counters));

        assistantPanel = new AssistantPanel();
        assistantPanel.setFocusable(false);
        assistantPanelWrapper = new JScrollPane(assistantPanel);

        this.position = new Position(configuration.getX(), configuration.getY());
        this.setLocation(position.getX(), position.getY());
        this.dimension = new Dimension(configuration.getWidth(), configuration.getHeight());
        this.setSize(dimension.width, dimension.height);

        addMouseListeners();

        // -------------------------------------------------------------------------------------------------------------
        // Not really needed for the time being - allows for seeing
        // the TAB with a regular KEY listener ...
        setFocusTraversalKeysEnabled(false);

        addKeyListeners();

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent ev) {
                if (controlActivated.get()) {
                    fireOnKeyReleased(-1, Character.MIN_VALUE);
                }
            }
        });

        onReady(); // the network has been before we've been registered as a
        // listener ...
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

    public void addListener(AssistantFrameListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onLocationUpdated(int x, int y) {
        this.position.setX(x);
        this.position.setY(y);
        configuration = new FrameConfiguration(position, dimension);
        configuration.persist(frameType);
    }

    @Override
    protected void onSizeUpdated(int width, int height) {
        dimension.setSize(width, height);
        configuration = new FrameConfiguration(position, dimension);
        configuration.persist(frameType);
    }

    private ToolBar createToolBar() {
        final ToolBar toolbar = new ToolBar();

        toolbar.addAction(startAction);
        toolbar.addAction(stopAction);
        toolbar.addSeparator();
        toolbar.addAction(networkConfigurationAction);
        toolbar.addAction(captureEngineConfigurationAction);
        toolbar.addAction(compressorEngineConfigurationAction);
        toolbar.addAction(resetAction);
        toolbar.addSeparator();
        toolbar.addToggleAction(createToggleControlMode());
        toolbar.addAction(remoteClipboardRequestAction);
        toolbar.addAction(remoteClipboardSetAction);
        toolbar.addSeparator();
        toolbar.addAction(lookAndFeelAction);
        toolbar.addSeparator();
        toolbar.addAction(createShowInfoAction());
        toolbar.addSeparator();
        toolbar.addAction(createShowHelpAction());
        toolbar.addGlue();
        toolbar.addAction(ipAddressAction);
        toolbar.addSeparator();
        toolbar.addAction(createExitAction());

        return toolbar;
    }

    private StatusBar createStatusBar(Set<Counter<?>> counters) {
        final StatusBar statusBar = new StatusBar();

        for (Counter<?> counter : counters) {
            statusBar.addSeparator();
            statusBar.addCounter(counter, counter.getWidth());
        }

        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.addSeparator();
        statusBar.addConnectionDuration();
        statusBar.add(Box.createHorizontalStrut(10));

        return statusBar;
    }

    private Action createToggleControlMode() {
        final Action showSystemInfo = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                controlActivated.set(!controlActivated.get());
            }
        };

        showSystemInfo.putValue(Action.NAME, "toggleControlMode");
        showSystemInfo.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("control.mode"));
        showSystemInfo.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.CONTROL));

        return showSystemInfo;
    }

    void onReady() {
        removeCenter();

        validate();
        repaint();

        startAction.setEnabled(true);
        stopAction.setEnabled(false);

        networkConfigurationAction.setEnabled(true);
        ipAddressAction.setEnabled(true);

        captureEngineConfigurationAction.setEnabled(true);
        resetAction.setEnabled(false);
        remoteClipboardRequestAction.setEnabled(false);
        remoteClipboardSetAction.setEnabled(false);
        lookAndFeelAction.setEnabled(true);

        statusBar.setMessage(Babylon.translate("ready"));
    }

    void onHttpStarting(int port) {
        startAction.setEnabled(false);
        stopAction.setEnabled(true);

        networkConfigurationAction.setEnabled(false);
        ipAddressAction.setEnabled(false);

        final ImageIcon waiting = ImageUtilities.getOrCreateIcon(ImageNames.WAITING);

        center = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                final int x = (getWidth() - waiting.getIconWidth()) / 2;
                final int y = (getHeight() - waiting.getIconHeight()) / 2;
                g.drawImage(waiting.getImage(), x, y, this);
            }
        };
        add(center, BorderLayout.CENTER);

        statusBar.setMessage(Babylon.translate("https.ready", port));
    }

    void onStarting(int port) {
        startAction.setEnabled(false);
        stopAction.setEnabled(true);

        networkConfigurationAction.setEnabled(false);
        ipAddressAction.setEnabled(false);

        statusBar.setMessage(Babylon.translate("starting", port));
    }

    void onAccepting(int port) {
        startAction.setEnabled(false);
        stopAction.setEnabled(true);

        networkConfigurationAction.setEnabled(false);
        ipAddressAction.setEnabled(false);

        statusBar.setMessage(Babylon.translate("accepting", port));
    }

    boolean onAccepted(Socket connection) {
        if (JOptionPane.showConfirmDialog(this, Babylon.translate("connection.incoming.msg1", connection.getInetAddress()),
                Babylon.translate("connection.incoming"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                ImageUtilities.getOrCreateIcon(ImageNames.USERS)) != JOptionPane.OK_OPTION) {
            return false;
        }

        removeCenter();

        statusBar.setMessage(Babylon.translate("connection.incoming.msg2", connection.getInetAddress()));
        center = assistantPanelWrapper;
        add(center, BorderLayout.CENTER);

        resetAction.setEnabled(true);
        remoteClipboardRequestAction.setEnabled(true);
        remoteClipboardSetAction.setEnabled(true);

        validate();
        repaint();

        return true;
    }

    void onClipboardRequested() {
        remoteClipboardRequestAction.setEnabled(false);
    }

    void onClipboardSending() {
        remoteClipboardSetAction.setEnabled(false);
    }

    void onClipboardSent() {
        remoteClipboardSetAction.setEnabled(true);
    }

    void onClipboardReceived() {
        remoteClipboardRequestAction.setEnabled(true);
    }

    void onSessionStarted() {
        long sessionStartTime = Instant.now().toEpochMilli();
        sessionTimer = new Timer(1000, e -> {
            final long endTime = Instant.now().toEpochMilli();
            final long secondsCounter = (endTime - sessionStartTime) / 1000;
            statusBar.setSessionDuration(String.format("%02d:%02d:%02d",(secondsCounter/3600), ((secondsCounter % 3600)/60), (secondsCounter % 60)));
        });
        sessionTimer.start();
    }

    void onDisconnecting() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
    }

    void onIOError(IOException error) {
        startAction.setEnabled(false);
        stopAction.setEnabled(false);

        resetAction.setEnabled(false);
        remoteClipboardRequestAction.setEnabled(false);
        remoteClipboardSetAction.setEnabled(false);

        sessionTimer.stop();

        removeCenter();

        validate();
        repaint();

        JOptionPane.showMessageDialog(this, Babylon.translate("comm.error.msg1", error.getMessage()), Babylon.translate("comm.error"),
                JOptionPane.ERROR_MESSAGE);
    }

    private void removeCenter() {
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
        assistantPanel.onMouseLocationUpdated(x, y);
    }

    private void fireOnMouseMove(int x, int y) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onMouseMove(x, y);
        }
    }

    private void fireOnMousePressed(int x, int y, int button) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onMousePressed(x, y, button);
        }
    }

    private void fireOnMouseReleased(int x, int y, int button) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onMouseReleased(x, y, button);
        }
    }

    private void fireOnMouseWheeled(int x, int y, int rotations) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onMouseWheeled(x, y, rotations);
        }
    }

    private void fireOnKeyPressed(int keycode, char keychar) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onKeyPressed(keycode, keychar);
        }
    }

    private void fireOnKeyReleased(int keycode, char keychar) {
        final List<AssistantFrameListener> xlisteners = listeners.getListeners();

        for (final AssistantFrameListener xlistener : xlisteners) {
            xlistener.onKeyReleased(keycode, keychar);
        }
    }

}
