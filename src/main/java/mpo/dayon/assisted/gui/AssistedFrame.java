package mpo.dayon.assisted.gui;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.Dimension;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

class AssistedFrame extends BaseFrame {
		private transient AssistedFrameConfiguration configuration;
		private final transient Position position;
		private final transient Dimension dimension;

	public AssistedFrame(AssistedFrameConfiguration configuration) {
		this.configuration = configuration;

		setTitle("Dayon! (" + Babylon.translate("assisted") + ") " + Version.get());

		setupToolBar(createToolBar());
		setupStatusBar(createStatusBar());
		
		this.position = new Position(configuration.getX(), configuration.getY());
		this.setLocation(position.getX(), position.getY());
		this.dimension = new Dimension(configuration.getWidth(), configuration.getHeight());
		this.setSize(dimension.getWidth(), dimension.getHeight());

		addWindowListener(new WindowAdapter() {
			@Override
            public void windowOpened(WindowEvent ev) {
				addComponentListener(new ComponentAdapter() {
					@Override
                    public void componentResized(ComponentEvent ev) {
						onSizeUpdated(getWidth(), getHeight());
					}

					@Override
                    public void componentMoved(ComponentEvent ev) {
						onLocationUpdated(getX(), getY());
					}
				});
			}
		});

		onReady();
	}

	private void onLocationUpdated(int x, int y) {
		this.position.setX(x);
		this.position.setY(y);
		configuration = new AssistedFrameConfiguration(position, dimension);
		configuration.persist(false);
	}

	private void onSizeUpdated(int width, int height) {
		dimension.setWidth(width);
		dimension.setHeight(height);
		configuration = new AssistedFrameConfiguration(position, dimension);
		configuration.persist(false);
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
