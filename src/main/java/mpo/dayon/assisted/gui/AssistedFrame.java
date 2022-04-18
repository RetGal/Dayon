package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.assisted.utils.ScreenUtilities;

import javax.swing.Action;
import javax.swing.Box;
import java.awt.Cursor;

import static mpo.dayon.common.babylon.Babylon.translate;

class AssistedFrame extends BaseFrame {
    private final Action startAction;
    private final Action stopAction;
    private final Action toggleMultiScreenCaptureAction;
    private final Cursor cursor = this.getCursor();
    private boolean connected;

    AssistedFrame(AssistedStartAction startAction, AssistedStopAction stopAction, Action toggleMultiScreenCaptureAction) {
        super.setFrameType(FrameType.ASSISTED);
        this.stopAction = stopAction;
        this.startAction = startAction;
        this.toggleMultiScreenCaptureAction = toggleMultiScreenCaptureAction;
        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar());
        onReady();
    }

    private ToolBar createToolBar() {
        final ToolBar toolbar = new ToolBar();
        toolbar.addAction(startAction);
        toolbar.addAction(stopAction);
        if (ScreenUtilities.getNumberOfScreens() > 1) {
            toolbar.addToggleAction(toggleMultiScreenCaptureAction);
        }
        toolbar.addSeparator();
        toolbar.addAction(createShowInfoAction());
        toolbar.addAction(createShowHelpAction());
        toolbar.addGlue();
        toolbar.addAction(createExitAction());
        return toolbar;
    }

    private StatusBar createStatusBar() {
        final StatusBar statusBar = new StatusBar();
        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.add(Box.createHorizontalStrut(10));
        return statusBar;
    }

    void onReady() {
        this.setCursor(cursor);
        startAction.setEnabled(true);
        stopAction.setEnabled(false);
        statusBar.setMessage(translate("ready"));
        connected = false;
    }

    void onConnecting(String serverName, int serverPort) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        startAction.setEnabled(false);
        stopAction.setEnabled(true);
        statusBar.setMessage(translate("connecting", serverName, serverPort));
        connected = false;
    }

    void onConnected() {
        this.setCursor(cursor);
        startAction.setEnabled(false);
        stopAction.setEnabled(true);
        statusBar.setMessage(translate("connected"));
        connected = true;
    }

    void onHostNotFound(String serverName) {
        this.setCursor(cursor);
        if (!connected) {
            startAction.setEnabled(true);
            stopAction.setEnabled(false);
            statusBar.setMessage(translate("serverNotFound", serverName));
        }
    }

    void onConnectionTimeout(String serverName, int serverPort) {
        this.setCursor(cursor);
        if (!connected) {
            stopAction.setEnabled(false);
            startAction.setEnabled(true);
            statusBar.setMessage(translate("connectionTimeout", serverName, serverPort));
        }
    }

    void onRefused(String serverName, int serverPort) {
        this.setCursor(cursor);
        if (!connected) {
            startAction.setEnabled(true);
            stopAction.setEnabled(false);
            statusBar.setMessage(translate("refused", serverName, serverPort));
        }
    }

    void onDisconnecting() {
        onReady();
    }
}
