package mpo.dayon.assisted.gui;

import javax.swing.Box;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.FrameConfiguration;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

import java.awt.*;

class AssistedFrame extends BaseFrame {
		private transient FrameConfiguration configuration;
		private final transient Position position;
		private final transient Dimension dimension;
		private final static FrameType FRAME_TYPE = FrameType.ASSISTED;

	public AssistedFrame(FrameConfiguration configuration) {
		this.configuration = configuration;

		setTitle("Dayon! (" + Babylon.translate("assisted") + ") " + Version.get());

		setupToolBar(createToolBar());
		setupStatusBar(createStatusBar());
		
		this.position = new Position(configuration.getX(), configuration.getY());
		this.setLocation(position.getX(), position.getY());
		this.dimension = new Dimension(configuration.getWidth(), configuration.getHeight());
		this.setSize(dimension.width, dimension.height);

		onReady();
	}

	@Override
	protected void onLocationUpdated(int x, int y) {
		this.position.setX(x);
		this.position.setY(y);
		configuration = new FrameConfiguration(position, dimension);
		configuration.persist(FRAME_TYPE);
	}

	@Override
	protected void onSizeUpdated(int width, int height) {
		dimension.setSize(width, height);
		configuration = new FrameConfiguration(position, dimension);
		configuration.persist(FRAME_TYPE);
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
