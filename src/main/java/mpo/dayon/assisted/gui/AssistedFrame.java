package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.assisted.utils.ScreenUtilities;
import mpo.dayon.common.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.toolbar.ToolBar.DEFAULT_SPACER;

class AssistedFrame extends BaseFrame {
    private final transient Action startAction;
    private final transient Action stopAction;
    private final transient Action toggleMultiScreenCaptureAction;
    private final Cursor mouseCursor = this.getCursor();
    private boolean connected;
    private ToolBar toolbar;

    AssistedFrame(Action startAction, Action stopAction, Action toggleMultiScreenCaptureAction) {
        super.setFrameType(FrameType.ASSISTED);
        this.stopAction = stopAction;
        this.startAction = startAction;
        this.toggleMultiScreenCaptureAction = toggleMultiScreenCaptureAction;
        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar());
        onReady();
    }

    private ToolBar createToolBar() {
        toolbar = new ToolBar();
        toolbar.addSeparator(DEFAULT_SPACER);
        toolbar.addAction(startAction);
        toolbar.addAction(stopAction);
        if (ScreenUtilities.getNumberOfScreens() > 1 || File.separatorChar == '\\') {
            toolbar.addSeparator();
            if (ScreenUtilities.getNumberOfScreens() > 1) {
                toolbar.addToggleAction(toggleMultiScreenCaptureAction);
            }
            if (File.separatorChar == '\\') {
                toolbar.addAction(createShowUacSettingsAction());
            }
        }
        toolbar.addSeparator();
        toolbar.add(toolbar.getFingerprints());
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
        final StatusBar statusBar = new StatusBar();
        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.add(Box.createHorizontalStrut(10));
        return statusBar;
    }

    void onReady() {
        this.setCursor(mouseCursor);
        startAction.setEnabled(true);
        stopAction.setEnabled(false);
        getStatusBar().setMessage(translate("ready"));
        connected = false;
    }

    void onConnecting(String serverName, int serverPort) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        startAction.setEnabled(false);
        stopAction.setEnabled(true);
        getStatusBar().setMessage(translate("connecting", serverName, serverPort));
        connected = false;
    }

    void onConnected(String fingerprints) {
        this.setCursor(mouseCursor);
        startAction.setEnabled(false);
        stopAction.setEnabled(true);
        setFingerprints(fingerprints);
        getStatusBar().setMessage(translate("connected"));
        connected = true;
    }

    void onHostNotFound(String serverName) {
        this.setCursor(mouseCursor);
        if (!connected) {
            startAction.setEnabled(true);
            stopAction.setEnabled(false);
            getStatusBar().setMessage(translate("serverNotFound", serverName));
        }
    }

    void onConnectionTimeout(String serverName, int serverPort) {
        this.setCursor(mouseCursor);
        if (!connected) {
            stopAction.setEnabled(false);
            startAction.setEnabled(true);
            getStatusBar().setMessage(translate("connectionTimeout", serverName, serverPort));
        }
    }

    void onRefused(String serverName, int serverPort) {
        this.setCursor(mouseCursor);
        if (!connected) {
            startAction.setEnabled(true);
            stopAction.setEnabled(false);
            getStatusBar().setMessage(translate("refused", serverName, serverPort));
        }
    }

    void onDisconnecting() {
        toolbar.clearFingerprints();
        onReady();
    }

    private static class AssistedAbstractAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent ev) {
            try {
                Runtime.getRuntime().exec(System.getenv("WINDIR") + "\\system32\\useraccountcontrolsettings.exe");
            } catch (IOException e) {
                Log.error(e.getMessage());
            }
        }
    }
}
