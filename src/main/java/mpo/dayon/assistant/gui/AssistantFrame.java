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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class AssistantFrame extends BaseFrame {

    private final transient Listeners<AssistantFrameListener> listeners = new Listeners<>();

    private final JScrollPane assistantPanelWrapper;

    private final AssistantPanel assistantPanel;
    
    private final transient AssistantActions actions;

    private Timer sessionTimer;

    @Nullable
    private JComponent center;

    private final AtomicBoolean controlActivated = new AtomicBoolean(false);

    AssistantFrame(AssistantActions actions, Set<Counter<?>> counters) {
        super.setFrameType(FrameType.ASSISTANT);

        setTitle("Dayon! (" + Babylon.translate("assistant") + ") " + Version.get());
        
        this.actions = actions;

        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar(counters));

        assistantPanel = new AssistantPanel();
        assistantPanel.setFocusable(false);
        assistantPanelWrapper = new JScrollPane(assistantPanel);

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

    private ToolBar createToolBar() {
        final ToolBar toolbar = new ToolBar();

        toolbar.addAction(actions.getStartAction());
        toolbar.addAction(actions.getStopAction());
        toolbar.addSeparator();
        toolbar.addAction(actions.getNetworkConfigurationAction());
        toolbar.addAction(actions.getCaptureEngineConfigurationAction());
        toolbar.addAction(actions.getCompressionEngineConfigurationAction());
        toolbar.addAction(actions.getResetAction());
        toolbar.addSeparator();
        toolbar.addToggleAction(createToggleControlMode());
        toolbar.addAction(actions.getRemoteClipboardRequestAction());
        toolbar.addAction(actions.getRemoteClipboardSetAction());
        toolbar.addSeparator();
        toolbar.addAction(actions.getLookAndFeelAction());
        toolbar.addSeparator();
        toolbar.addAction(createShowInfoAction());
        toolbar.addSeparator();
        toolbar.addAction(createShowHelpAction());
        toolbar.addGlue();
        toolbar.addAction(actions.getIpAddressAction());
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

        actions.getStartAction().setEnabled(true);
        actions.getStopAction().setEnabled(false);

        actions.getNetworkConfigurationAction().setEnabled(true);
        actions.getIpAddressAction().setEnabled(true);

        actions.getCaptureEngineConfigurationAction().setEnabled(true);
        actions.getResetAction().setEnabled(false);
        actions.getRemoteClipboardRequestAction().setEnabled(false);
        actions.getRemoteClipboardSetAction().setEnabled(false);
        actions.getLookAndFeelAction().setEnabled(true);

        statusBar.setMessage(Babylon.translate("ready"));
    }

    void onHttpStarting(int port) {
        actions.getStartAction().setEnabled(false);
        actions.getStopAction().setEnabled(true);

        actions.getNetworkConfigurationAction().setEnabled(false);
        actions.getIpAddressAction().setEnabled(false);

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
        actions.getStartAction().setEnabled(false);
        actions.getStopAction().setEnabled(true);

        actions.getNetworkConfigurationAction().setEnabled(false);
        actions.getIpAddressAction().setEnabled(false);

        statusBar.setMessage(Babylon.translate("starting", port));
    }

    void onAccepting(int port) {
        actions.getStartAction().setEnabled(false);
        actions.getStopAction().setEnabled(true);

        actions.getNetworkConfigurationAction().setEnabled(false);
        actions.getIpAddressAction().setEnabled(false);

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

        actions.getResetAction().setEnabled(true);
        actions.getRemoteClipboardRequestAction().setEnabled(true);
        actions.getRemoteClipboardSetAction().setEnabled(true);

        validate();
        repaint();

        return true;
    }

    void onClipboardRequested() {
        actions.getRemoteClipboardRequestAction().setEnabled(false);
    }

    void onClipboardSending() {
        actions.getRemoteClipboardSetAction().setEnabled(false);
    }

    void onClipboardSent() {
        actions.getRemoteClipboardSetAction().setEnabled(true);
    }

    void onClipboardReceived() {
        actions.getRemoteClipboardRequestAction().setEnabled(true);
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
        actions.getStartAction().setEnabled(false);
        actions.getStopAction().setEnabled(false);

        actions.getResetAction().setEnabled(false);
        actions.getRemoteClipboardRequestAction().setEnabled(false);
        actions.getRemoteClipboardSetAction().setEnabled(false);

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
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onMouseMove(x, y);
        }
    }

    private void fireOnMousePressed(int x, int y, int button) {
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onMousePressed(x, y, button);
        }
    }

    private void fireOnMouseReleased(int x, int y, int button) {
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onMouseReleased(x, y, button);
        }
    }

    private void fireOnMouseWheeled(int x, int y, int rotations) {
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onMouseWheeled(x, y, rotations);
        }
    }

    private void fireOnKeyPressed(int keyCode, char keyChar) {
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onKeyPressed(keyCode, keyChar);
        }
    }

    private void fireOnKeyReleased(int keyCode, char keyChar) {
        for (final AssistantFrameListener xListener : listeners.getListeners()) {
            xListener.onKeyReleased(keyCode, keyChar);
        }
    }

}
