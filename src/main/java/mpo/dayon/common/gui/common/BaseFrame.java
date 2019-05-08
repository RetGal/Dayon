package mpo.dayon.common.gui.common;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import mpo.dayon.common.version.Version;

public abstract class BaseFrame extends JFrame {

	private transient FrameConfiguration configuration;

	private transient Position position;

	private Dimension dimension;

	private FrameType frameType;

	private ToolBar toolBar;

	protected StatusBar statusBar;

	protected BaseFrame() {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setIconImage(ImageUtilities.getOrCreateIcon(ImageNames.APP).getImage());

		addWindowListener(new WindowAdapter() {
			@Override
            public void windowClosing(WindowEvent ev) {
				doExit();
			}
		});
		addSizeAndPositionListener();
	}

	private void doExit() {
		if (JOptionPane.showConfirmDialog(this, Babylon.translate("exit.confirm"), Babylon.translate("exit"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
			Log.info("Bye!");
			System.exit(0);
		}
	}

	protected void setFrameType(FrameType frameType) {
		this.frameType = frameType;
		this.configuration = new FrameConfiguration(frameType);
		this.position = new Position(configuration.getX(), configuration.getY());
		this.setLocation(position.getX(), position.getY());
		this.dimension = new Dimension(configuration.getWidth(), configuration.getHeight());
		this.setSize(dimension.width, dimension.height);
	}

	protected void setupToolBar(ToolBar toolBar) {
		this.toolBar = toolBar;
		add(toolBar, BorderLayout.NORTH);
	}

	protected void setupStatusBar(StatusBar statusBar) {
		this.statusBar = statusBar;
		add(statusBar, BorderLayout.SOUTH);
	}

	protected Action createExitAction() {
		final Action exit = new AbstractAction() {

			@Override
            public void actionPerformed(ActionEvent ev) {
				doExit();
			}
		};

		exit.putValue(Action.NAME, "exit");
		exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("exit.dayon"));
		exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.EXIT));

		return exit;
	}

	private static final String HTTP_HOME = "https://github.com/retgal/dayon";
	private static final String HTTP_SUPPORT = "https://retgal.github.io/Dayon/support" + Babylon.translate("language.suffix") + ".html";
	private static final String HTTP_FEEDBACK = "https://github.com/retgal/dayon/issues";

	protected Action createShowInfoAction() {
		final Action showSystemInfo = new AbstractAction() {

			@Override
            public void actionPerformed(ActionEvent ev) {
				final JTextArea props = new JTextArea(SystemUtilities.getSystemPropertiesEx());
				props.setEditable(false);

				final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
				props.setFont(font);

				final JPanel panel = new JPanel();
				panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

				panel.setPreferredSize(new Dimension(500, 300));

				final JLabel info = new JLabel(composeLabelHtml("Dayon!",  Babylon.translate("synopsys")));
				info.setAlignmentX(Component.LEFT_ALIGNMENT);
				info.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						browse(HTTP_HOME);
					}
				});

				final JLabel version = new JLabel(composeLabelHtml(Babylon.translate("version.installed"), Version.get().toString()));
				version.setAlignmentX(Component.LEFT_ALIGNMENT);
				version.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						browse(Version.RELEASE_LOCATION + Version.get());
					}
				});

				final JLabel latest = new JLabel(composeLabelHtml(Babylon.translate("version.latest"), Version.get().getLatestRelease()));
				version.setAlignmentX(Component.LEFT_ALIGNMENT);
				latest.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						browse(Version.RELEASE_LOCATION + Version.get().getLatestRelease());
					}
				});

				final JLabel support = new JLabel(composeLabelHtml(Babylon.translate("support"), HTTP_SUPPORT));
				support.setAlignmentX(Component.LEFT_ALIGNMENT);
				support.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						browse(HTTP_SUPPORT);
					}
				});

				final JLabel feedback = new JLabel(composeLabelHtml(Babylon.translate("feedback"), HTTP_FEEDBACK));
				feedback.setAlignmentX(Component.LEFT_ALIGNMENT);
				feedback.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						browse(HTTP_FEEDBACK);
					}
				});

				final JScrollPane spane = new JScrollPane(props);
				spane.setAlignmentX(Component.LEFT_ALIGNMENT);

				panel.add(Box.createVerticalStrut(10));
				panel.add(info);
				panel.add(Box.createVerticalStrut(5));
				panel.add(version);
				if (Version.get().getLatestRelease() != null) {
					panel.add(Box.createVerticalStrut(5));
					panel.add(latest);
				}
				panel.add(Box.createVerticalStrut(10));
				panel.add(spane);
				panel.add(Box.createVerticalStrut(10));
				panel.add(support);
				panel.add(Box.createVerticalStrut(5));
				panel.add(feedback);

				JOptionPane.showMessageDialog(BaseFrame.this, panel, Babylon.translate("system.info"), JOptionPane.INFORMATION_MESSAGE,
						ImageUtilities.getOrCreateIcon(ImageNames.APP_LARGE));

			}
		};

		showSystemInfo.putValue(Action.NAME, "showSystemInfo");
		showSystemInfo.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("system.info.show"));
		showSystemInfo.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.INFO));

		return showSystemInfo;
	}

	private void addSizeAndPositionListener() {
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
	}

	private void onSizeUpdated(int width, int height) {
		this.dimension.setSize(width, height);
		configuration = new FrameConfiguration(position, dimension);
		configuration.persist(frameType);
	}

	private void onLocationUpdated(int x, int y) {
		this.position = new Position(x, y);
		configuration = new FrameConfiguration(position, dimension);
		configuration.persist(frameType);
	}

	private String composeLabelHtml(String label, String url) {
		return "<html>" + label + " : <a href=''>" + url + "</a></html>";
	}

	protected Action createShowHelpAction() {
		final Action showHelp = new AbstractAction() {
			@Override
            public void actionPerformed(ActionEvent ev) {
                browse(SystemUtilities.getLocalIndexHtml());
			}
		};

		showHelp.putValue(Action.NAME, "showHelp");
		showHelp.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("help"));
		showHelp.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.HELP));

		return showHelp;
	}
	
	private static void browse(String url) {
		try {
			browse(new URI(url));
		} catch (URISyntaxException ex) {
			Log.warn(ex);
		}
	}
	
	private static void browse(URI uri) {
		try {
			if (Desktop.isDesktopSupported()) {
				final Desktop desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.BROWSE)) {
					desktop.browse(uri);
				}
			}
		} catch (Exception ex) {
			Log.warn(ex);
		}
	}

	public ToolBar getToolBar() {
		return toolBar;
	}
}
