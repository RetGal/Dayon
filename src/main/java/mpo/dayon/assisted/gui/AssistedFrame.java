package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.network.NetworkAssistedEngine;
import mpo.dayon.assisted.utils.ScreenUtilities;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import static mpo.dayon.common.babylon.Babylon.translate;

class AssistedFrame extends BaseFrame {
    private final transient Action startAction;
    private final transient Action stopAction;
    private final transient Action toggleMultiScreenCaptureAction;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton connectionSettingsButton;
    private final Cursor mouseCursor = this.getCursor();
    private boolean connected;
    private Timer peerStatusTimer;

    AssistedFrame(Action startAction, Action stopAction, Action toggleMultiScreenCaptureAction, NetworkAssistedEngine networkEngine) {
        super.setFrameType(FrameType.ASSISTED);
        this.stopAction = stopAction;
        this.startAction = startAction;
        this.startButton = createButton(this.startAction);
        this.stopButton = createButton(this.stopAction, false);
        this.connectionSettingsButton = createButton(createAssistedConnectionSettingsAction(networkEngine));
        this.toggleMultiScreenCaptureAction = toggleMultiScreenCaptureAction;
        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar());
        onReady();
    }

    private ToolBar createToolBar() {
        ToolBar toolbar = new ToolBar();
        toolbar.add(startButton);
        toolbar.add(stopButton);
        toolbar.addSeparator();
        toolbar.add(connectionSettingsButton);
        if (ScreenUtilities.getNumberOfScreens() > 1 || File.separatorChar == '\\') {
            toolbar.addSeparator();
            if (ScreenUtilities.getNumberOfScreens() > 1) {
                toolbar.addToggleAction(toggleMultiScreenCaptureAction);
            }
            if (File.separatorChar == '\\') {
                toolbar.addAction(createShowUacSettingsAction());
            }
            toolbar.addSeparator();
        }
        toolbar.add(getFingerprints());
        toolbar.addGlue();
        return toolbar;
    }

    private Action createShowUacSettingsAction() {
        final Action showUacSettings = new AssistedAbstractAction();
        showUacSettings.putValue(Action.SHORT_DESCRIPTION, translate("uacSettings"));
        showUacSettings.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.SHIELD));
        return showUacSettings;
    }

    private StatusBar createStatusBar() {
        final StatusBar statusBar = new StatusBar(15);
        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.add(Box.createHorizontalStrut(5));
        return statusBar;
    }

    void onReady() {
        this.setCursor(mouseCursor);
        toggleStartButton(true);
        connectionSettingsButton.setEnabled(true);
        getStatusBar().setMessage(translate("ready"));
        if (peerStatusTimer != null) {
            peerStatusTimer.stop();
        }
        getStatusBar().resetPortStateIndicator();
        getStatusBar().resetPeerStateIndicator();
        connected = false;
    }

    private void toggleStartButton(boolean enable) {
        startAction.setEnabled(enable);
        stopAction.setEnabled(!enable);
        startButton.setVisible(enable);
        stopButton.setVisible(!enable);
    }

    void onConnecting(String serverName, int serverPort) {
        toggleStartButton(false);
        connectionSettingsButton.setEnabled(false);
        getStatusBar().setMessage(translate("connecting", serverName, serverPort));
        connected = false;
    }

    void onConnected(String fingerprints) {
        // must be an inverted connection -> peer status always red
        if (peerStatusTimer != null) {
            peerStatusTimer.stop();
            getStatusBar().setPeerStateIndicator(Color.red);
        }
        this.setCursor(mouseCursor);
        toggleStartButton(false);
        setFingerprints(fingerprints);
        getStatusBar().setMessage(translate("connected"));
        connected = true;
    }

    void onHostNotFound(String serverName) {
        this.setCursor(mouseCursor);
        if (!connected) {
            toggleStartButton(true);
            getStatusBar().setMessage(translate("serverNotFound", serverName));
        }
    }

    void onConnectionTimeout(String serverName, int serverPort) {
        this.setCursor(mouseCursor);
        if (!connected) {
            toggleStartButton(true);
            getStatusBar().setMessage(translate("connectionTimeout", serverName, serverPort));
        }
    }

    void onRefused(String serverName, int serverPort) {
        this.setCursor(mouseCursor);
        if (!connected) {
            toggleStartButton(true);
            getStatusBar().setMessage(translate("refused", serverName, serverPort));
        }
    }

    void onDisconnecting() {
        clearFingerprints();
        onReady();
    }

    public void onPeerIsAccessible(boolean isPeerAccessible) {
        getStatusBar().setPortStateIndicator(isPeerAccessible ? Color.green : Color.red);
        getStatusBar().setPeerStateIndicator(isPeerAccessible ? Color.green : Color.red);
    }

    public void onAccepting(int port) {
        getStatusBar().setPortStateIndicator(Color.orange);
        getStatusBar().setMessage(translate("accepting", port));
        final boolean[] dimm = {false};
        peerStatusTimer = new Timer(1000, e -> {
            getStatusBar().setPeerStateIndicator(dimm[0] ? Color.red : Color.gray);
            dimm[0] = !dimm[0];
        });
        peerStatusTimer.start();
    }

    private static class AssistedAbstractAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent ev) {
            try {
                Desktop.getDesktop().open(new File(System.getenv("WINDIR") + "\\system32\\useraccountcontrolsettings.exe"));
            } catch (IOException e) {
                Log.error(e.getMessage());
            }
        }
    }
}
