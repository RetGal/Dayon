package mpo.dayon.assistant.gui;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.assistant.monitoring.counter.Counter;
import mpo.dayon.assistant.resource.ImageNames;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.common.Dimension;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

public class AssistantFrame extends BaseFrame {

	private final Listeners<AssistantFrameListener> listeners = new Listeners<>(AssistantFrameListener.class);

	private final JScrollPane assistantPanelWrapper;

	private final AssistantPanel assistantPanel;

	private final Action ipAddressAction;

	private final Action networkConfigurationAction;

	private final Action captureEngineConfigurationAction;

	private final Action compressorEngineConfigurationAction;

	private final Action resetAction;

	private final Action lookAndFeelAction;

	private final Action startAction;

	private final Action stopAction;
	
	private final Position position;
	
	private final Dimension dimension;

	private AssistantFrameConfiguration configuration;

	@Nullable
	private JComponent center;

	private final AtomicBoolean controlActivated = new AtomicBoolean(false);

	public AssistantFrame(AssistantFrameConfiguration configuration, Action ipAddressAction, Action networkConfigurationAction,
			Action captureEngineConfigurationAction, Action compressorEngineConfigurationAction, Action resetAction, Action lookAndFeelAction,
			AssistantStartAction startAction, AssistantStopAction stopAction, Counter<?>... counters) {
		this.configuration = configuration;

		setTitle("Dayon! (" + Babylon.translate("assistant") + ") " + Version.get());

		this.ipAddressAction = ipAddressAction;
		this.networkConfigurationAction = networkConfigurationAction;
		this.captureEngineConfigurationAction = captureEngineConfigurationAction;
		this.compressorEngineConfigurationAction = compressorEngineConfigurationAction;
		this.resetAction = resetAction;
		this.lookAndFeelAction = lookAndFeelAction;
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
		this.setSize(dimension.getWidth(), dimension.getHeight());

		addWindowListener(new WindowAdapter() {
			public void windowOpened(WindowEvent ev) {
				addComponentListener(new ComponentAdapter() {
					public void componentResized(ComponentEvent ev) {
						onSizeUpdated(getWidth(), getHeight());
					}

					public void componentMoved(ComponentEvent ev) {
						onLocationUpdated(getX(), getY());
					}
				});
			}
		});

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
			public void mouseDragged(MouseEvent ev) {
				if (controlActivated.get()) {
					fireOnMouseMove(ev.getX(), ev.getY());
				}
			}

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

		// -------------------------------------------------------------------------------------------------------------
		// Not really needed for the time being - allows for seeing
		// the TAB with a regular KEY listener ...
		setFocusTraversalKeysEnabled(false);

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

	public void addListener(AssistantFrameListener listener) {
		listeners.add(listener);
	}

	public void removeListener(AssistantFrameListener listener) {
		listeners.remove(listener);
	}

	private void onLocationUpdated(int x, int y) {
		this.position.setX(x);
		this.position.setY(y);
		configuration = new AssistantFrameConfiguration(position, dimension);
		configuration.persist(false);
	}

	private void onSizeUpdated(int width, int height) {
		dimension.setWidth(width);
		dimension.setHeight(height);
		configuration = new AssistantFrameConfiguration(position, dimension);
		configuration.persist(false);
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

	private StatusBar createStatusBar(Counter<?>[] counters) {
		final StatusBar statusBar = new StatusBar();

		for (Counter<?> counter : counters) {
			statusBar.addSeparator();
			statusBar.addCounter(counter, counter.getWidth());
		}

		statusBar.addSeparator();
		statusBar.addRamInfo();
		statusBar.add(Box.createHorizontalStrut(10));

		return statusBar;
	}

	private Action createToggleControlMode() {
		final Action showSystemInfo = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				controlActivated.set(!controlActivated.get());
			}
		};

		showSystemInfo.putValue(Action.NAME, "toggleControlMode");
		showSystemInfo.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("control.mode"));
		showSystemInfo.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.CONTROL));

		return showSystemInfo;
	}

	public void onReady() {
		removeCenter();

		validate();
		repaint();

		startAction.setEnabled(true);
		stopAction.setEnabled(false);

		networkConfigurationAction.setEnabled(true);
		ipAddressAction.setEnabled(true);

		captureEngineConfigurationAction.setEnabled(true);
		resetAction.setEnabled(false);
		lookAndFeelAction.setEnabled(true);

		statusBar.setMessage(Babylon.translate("ready"));
	}

	public void onHttpStarting(int port) {
		startAction.setEnabled(false);
		stopAction.setEnabled(true);

		networkConfigurationAction.setEnabled(false);
		ipAddressAction.setEnabled(false);

		final ImageIcon waiting = ImageUtilities.getOrCreateIcon(ImageNames.WAITING);

		JPanel pane = new JPanel() {

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				final int x = (getWidth() - waiting.getIconWidth()) / 2;
				final int y = (getHeight() - waiting.getIconHeight()) / 2;

				g.drawImage(waiting.getImage(), x, y, this);
			}
		};

		add(center = pane, BorderLayout.CENTER);

		statusBar.setMessage(Babylon.translate("https.ready", port));
	}

	public void onStarting(int port) {
		startAction.setEnabled(false);
		stopAction.setEnabled(true);

		networkConfigurationAction.setEnabled(false);
		ipAddressAction.setEnabled(false);

		statusBar.setMessage(Babylon.translate("starting", port));
	}

	public void onAccepting(int port) {
		startAction.setEnabled(false);
		stopAction.setEnabled(true);

		networkConfigurationAction.setEnabled(false);
		ipAddressAction.setEnabled(false);

		statusBar.setMessage(Babylon.translate("accepting", port));
	}

	public boolean onAccepted(Socket connection) {
		if (JOptionPane.showConfirmDialog(this, Babylon.translate("connection.incoming.msg1", connection.getInetAddress()),
				Babylon.translate("connection.incoming"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
				ImageUtilities.getOrCreateIcon(ImageNames.USERS)) != JOptionPane.OK_OPTION) {
			return false;
		}

		removeCenter();

		statusBar.setMessage(Babylon.translate("connection.incoming.msg2", connection.getInetAddress()));
		add(center = assistantPanelWrapper, BorderLayout.CENTER);

		resetAction.setEnabled(true);

		validate();
		repaint();

		return true;
	}

	public void onIOError(IOException error) {
		startAction.setEnabled(false);
		stopAction.setEnabled(false);

		resetAction.setEnabled(false);

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

	public void onCaptureUpdated(final int captureId, final BufferedImage captureImage) {
		assistantPanel.onCaptureUpdated(captureId, captureImage);
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	public void onMouseLocationUpdated(int x, int y) {
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
