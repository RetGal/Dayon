package mpo.dayon.assisted.gui;

import javax.swing.*;

import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

class AssistedFrame extends BaseFrame {
	private final transient Action startAction;
	private final transient Action stopAction;
	private boolean connected;

	public AssistedFrame(AssistedStartAction startAction, AssistedStopAction stopAction) {
		super.setFrameType(FrameType.ASSISTED);
		setTitle(format("Dayon! (%s) %s", translate("assisted"), Version.get()));
		this.stopAction = stopAction;
		this.startAction = startAction;
		setupToolBar(createToolBar());
		setupStatusBar(createStatusBar());
		onReady();
	}

	private ToolBar createToolBar() {
		final ToolBar toolbar = new ToolBar();
		toolbar.addAction(startAction);
		toolbar.addAction(stopAction);
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
		startAction.setEnabled(true);
		stopAction.setEnabled(false);
		statusBar.setMessage(translate("ready"));
		connected = false;
	}

	void onConnecting(String serverName, int serverPort) {
		startAction.setEnabled(false);
		stopAction.setEnabled(true);
		statusBar.setMessage(translate("connecting", serverName, serverPort));
		connected = false;
	}

	void onConnected() {
		startAction.setEnabled(false);
		stopAction.setEnabled(true);
		statusBar.setMessage(translate("connected"));
		connected = true;
	}

	void onHostNotFound(String serverName) {
		if (!connected) {
			startAction.setEnabled(true);
			stopAction.setEnabled(false);
			statusBar.setMessage(translate("serverNotFound", serverName));
		}
	}

	void onConnectionTimeout(String serverName, int serverPort) {
		if (!connected) {
			stopAction.setEnabled(false);
			startAction.setEnabled(true);
			statusBar.setMessage(translate("connectionTimeout", serverName, serverPort));
		}
	}

	void onRefused(String serverName, int serverPort) {
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
