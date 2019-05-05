package mpo.dayon.assisted.gui;

import javax.swing.Box;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

class AssistedFrame extends BaseFrame {

	public AssistedFrame() {
		super.setFrameType(FrameType.ASSISTED);

		setTitle("Dayon! (" + Babylon.translate("assisted") + ") " + Version.get());

		setupToolBar(createToolBar());
		setupStatusBar(createStatusBar());

		onReady();
	}

	private ToolBar createToolBar() {
		final ToolBar toolbar = new ToolBar();

		toolbar.addAction(createShowInfoAction());
		toolbar.addSeparator();
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

	private void onReady() {
		statusBar.setMessage(Babylon.translate("ready"));
	}

	public void onHttpConnecting(NetworkAssistedEngineConfiguration configuration) {
		statusBar.setMessage(Babylon.translate("https.handshake", configuration.getServerName(), configuration.getServerPort()));
	}

	public void onConnecting(NetworkAssistedEngineConfiguration configuration) {
		statusBar.setMessage(Babylon.translate("connecting", configuration.getServerName(), configuration.getServerPort()));
	}

	public void onConnected() {
		statusBar.setMessage(Babylon.translate("connected"));
	}
}
