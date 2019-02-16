package mpo.dayon.assistant.gui;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import mpo.dayon.assistant.control.ControlEngine;
import mpo.dayon.assistant.control.ControlEngineConfiguration;
import mpo.dayon.assistant.decompressor.DeCompressorEngine;
import mpo.dayon.assistant.decompressor.DeCompressorEngineConfiguration;
import mpo.dayon.assistant.decompressor.DeCompressorEngineListener;
import mpo.dayon.common.monitoring.counter.BitCounter;
import mpo.dayon.common.monitoring.counter.CaptureCompressionCounter;
import mpo.dayon.common.monitoring.counter.MergedTileCounter;
import mpo.dayon.common.monitoring.counter.SkippedTileCounter;
import mpo.dayon.common.monitoring.counter.TileCounter;
import mpo.dayon.assistant.network.NetworkAssistantConfiguration;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.network.NetworkAssistantEngineListener;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.assistant.utils.NetworkUtilities;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkMouseLocationMessageHandler;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.utils.Pair;
import mpo.dayon.common.utils.SystemUtilities;

public class Assistant implements Configurable<AssistantConfiguration>, ClipboardOwner {

	private final NetworkAssistantEngine network;

	private final ControlEngine control;

	private final BitCounter receivedBitCounter;

	private final TileCounter receivedTileCounter;

	private final SkippedTileCounter skippedTileCounter;

	private final MergedTileCounter mergedTileCounter;

	private final CaptureCompressionCounter captureCompressionCounter;

	private AssistantFrame frame;

	private AssistantConfiguration configuration;

	private NetworkAssistantConfiguration networkConfiguration;

	private CaptureEngineConfiguration captureEngineConfiguation;

	private CompressorEngineConfiguration compressorEngineConfiguation;

	private final Object prevBufferLOCK = new Object();

	private byte[] prevBuffer = null;

	private int prevWidth = -1;

	private int prevHeight = -1;

	public Assistant() {
		receivedBitCounter = new BitCounter("receivedBits", Babylon.translate("networkBandwidth"));
		receivedBitCounter.start(1000);

		receivedTileCounter = new TileCounter("receivedTiles", Babylon.translate("receivedTileNumber"));
		receivedTileCounter.start(1000);

		skippedTileCounter = new SkippedTileCounter("skippedTiles", Babylon.translate("skippedCaptureNumber"));
		skippedTileCounter.start(1000);

		mergedTileCounter = new MergedTileCounter("mergedTiles", Babylon.translate("mergedCaptureNumber"));
		mergedTileCounter.start(1000);

		captureCompressionCounter = new CaptureCompressionCounter("captureCompression", Babylon.translate("captureCompression"));
		captureCompressionCounter.start(1000);

		DeCompressorEngine decompressor = new DeCompressorEngine();

		decompressor.configure(new DeCompressorEngineConfiguration());
		decompressor.addListener(new MyDeCompressorEngineListener());
		decompressor.start(8);

		NetworkMouseLocationMessageHandler mouseHandler = mouse -> frame.onMouseLocationUpdated(mouse.getX(), mouse.getY());

		network = new NetworkAssistantEngine(decompressor, mouseHandler, this);

		network.configure(networkConfiguration = new NetworkAssistantConfiguration());
		network.addListener(new MyNetworkAssistantEngineListener());

		control = new ControlEngine(network);
		control.configure(new ControlEngineConfiguration());
		control.start();

		captureEngineConfiguation = new CaptureEngineConfiguration();
		compressorEngineConfiguation = new CompressorEngineConfiguration();
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable transferable) {}

	public void configure(AssistantConfiguration configuration) {
		this.configuration = configuration;

		final String lnf = configuration.getLookAndFeelClassName();
		try {
			UIManager.setLookAndFeel(lnf);
		} catch (Exception ex) {
			Log.warn("Cound not set the [" + lnf + "] L&F!", ex);
		}
	}

	public void start() {
		frame = new AssistantFrame(new AssistantFrameConfiguration(), createWhatIsMyIpAction(), createNetworkAssistantConfigurationAction(),
				createCaptureConfigurationAction(), createComressionConfigurationAction(), createResetAction(), createSwitchLookAndFeelAction(),
				createRemoteClipboardRequestAction(), createRemoteClipboardUpdateAction(), new AssistantStartAction(network), new AssistantStopAction(network), receivedBitCounter, captureCompressionCounter, receivedTileCounter,
				skippedTileCounter, mergedTileCounter);

		FatalErrorHandler.attachFrame(frame);


		frame.addListener(control);
		frame.setVisible(true);
	}

	private Action createWhatIsMyIpAction() {
		final Action ip = new AbstractAction() {
			private String pip;

			public void actionPerformed(ActionEvent ev) {
				final JButton button = (JButton) ev.getSource();

				final JPopupMenu choices = new JPopupMenu();

				if (pip == null) {
					final JMenuItem menuItem = new JMenuItem(Babylon.translate("retrieveMe"));
					menuItem.addActionListener(ev16 -> {
                        final Cursor cursor = frame.getCursor();

                        try {
                            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                            final URL url = new URL("http://dayonhome.sourceforge.net/whatismyip.php");
                            final URLConnection conn = url.openConnection();

                            final InputStream in = conn.getInputStream();
                            final BufferedReader lines = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                            String line;
                            while ((line = lines.readLine()) != null) {
                                pip = line;
                            }

                            SystemUtilities.safeClose(in);
                        } catch (IOException ex) {
                            Log.error("What is my IP error!", ex);
                        } finally {
                            frame.setCursor(cursor);
                        }

                        if (pip == null) {
                            JOptionPane.showMessageDialog(frame, Babylon.translate("ipAddress.msg1"), Babylon.translate("ipAddress"),
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            button.setText(pip);
                        }
                    });
					choices.add(menuItem);
				} else {
					final JMenuItem menuItem = new JMenuItem(Babylon.translate("ipAddressPublic", pip));
					menuItem.addActionListener(ev15 -> button.setText(pip));
					choices.add(menuItem);
				}

				final List<String> addrs = NetworkUtilities.getInetAddresses();
				for (String addr : addrs) {
					final JMenuItem menuItem = new JMenuItem(addr);
					menuItem.addActionListener(ev14 -> button.setText(menuItem.getText()));
					choices.add(menuItem);
				}

				choices.addSeparator();
				{
					final JMenuItem menuItem = new JMenuItem(Babylon.translate("copy.msg1"));
					// menuItem.setIcon(ImageUtilities.getOrCreateIcon(ImageNames.COPY));
					menuItem.addActionListener(ev13 -> {
                        final String url = "https://" + button.getText() + ":" + network.getPort() + "/dayon.html";

                        final StringSelection value = new StringSelection(url);
                        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(value, value);
                    });
					choices.add(menuItem);
				}
				{
					final JMenuItem menuItem = new JMenuItem(Babylon.translate("copy.msg2"));
					// menuItem.setIcon(ImageUtilities.getOrCreateIcon(ImageNames.COPY));
					menuItem.addActionListener(ev12 -> {
                        final String url = button.getText() + " " + network.getPort();

                        final StringSelection value = new StringSelection(url);
                        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(value, value);
                    });
					choices.add(menuItem);
				}

				choices.addSeparator();
				final JMenuItem help = new JMenuItem(Babylon.translate("help"));
				// help.setIcon(ImageUtilities.getOrCreateIcon(ImageNames.INFO_SMALL));
				help.addActionListener(ev1 -> {
                    try {
                        final URI uri = SystemUtilities.getLocalIndexHtml();

                        if (uri!= null && Desktop.isDesktopSupported()) {
                            final Desktop desktop = Desktop.getDesktop();
                            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                desktop.browse(uri);
                            }
                        }
                    } catch (IOException ex) {
                        Log.warn("Help Error!", ex);
                    }
                });
				choices.add(help);

				// -- display the menu
				// ---------------------------------------------------------------------------------

				final Point where = MouseInfo.getPointerInfo().getLocation();

				// *** HACK
				// ********************************************************************************************
				SwingUtilities.convertPointFromScreen(where, frame);
				choices.show(frame, where.x, where.y);

				final Point _choicesLocation = choices.getLocationOnScreen();
				final Point _frameLocation = frame.getLocationOnScreen();
				final int _xoffset = (_choicesLocation.x + choices.getWidth()) - (_frameLocation.x + frame.getWidth() - frame.getInsets().right);

				final Point _toolbarLocation = frame.getToolBar().getLocationOnScreen();
				final int _yoffset = _toolbarLocation.y + frame.getToolBar().getHeight() - _choicesLocation.y;

				choices.setLocation(_choicesLocation.x - _xoffset, _choicesLocation.y + _yoffset);
			}
		};

		ip.putValue(Action.NAME, "whatIsMyIpAddress");
		ip.putValue("DISPLAY_NAME", network.getLocalhost()); // always a selection
															// ...
		ip.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("ipAddress.msg1"));

		return ip;
	}

	private Action createNetworkAssistantConfigurationAction() {
		final Action exit = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				JFrame frame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

				final JPanel pane = new JPanel();

				pane.setLayout(new GridLayout(1, 2, 10, 10));

				final JLabel portNumberLbl = new JLabel(Babylon.translate("connection.settings.portNumber"));
				portNumberLbl.setToolTipText(Babylon.translate("connection.settings.portNumber.tooltip"));

				final JTextField portNumberTextField = new JTextField();
				portNumberTextField.setText(String.valueOf(networkConfiguration.getPort()));

				pane.add(portNumberLbl);
				pane.add(portNumberTextField);

				final boolean ok = DialogFactory.showOkCancel(frame, Babylon.translate("connection.network.settings"), pane, () -> {
                    final String portNumber = portNumberTextField.getText();
                    if (portNumber.isEmpty()) {
                        return Babylon.translate("connection.settings.emptyPortNumber");
                    }

                    try {
                        Integer.valueOf(portNumber);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("connection.settings.invalidPortNumber");
                    }

                    return null;
                });

				if (ok) {
					final NetworkAssistantConfiguration xnetworkConfiguration = new NetworkAssistantConfiguration(
							Integer.parseInt(portNumberTextField.getText()));

					if (!xnetworkConfiguration.equals(networkConfiguration)) {
						networkConfiguration = xnetworkConfiguration;
						networkConfiguration.persist();

						network.reconfigure(networkConfiguration);
					}
				}
			}
		};

		exit.putValue(Action.NAME, "networkAssistantConfiguration");
		exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("connection.network.settings"));
		exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.NETWORK_SETTINGS));

		return exit;
	}

	private Action createRemoteClipboardRequestAction() {
		final Action getRemoteClipboard = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				sendRemoteClipboardRequest();
			}
		};

		getRemoteClipboard.putValue(Action.NAME, "getClipboard");
		getRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("clipboard.getRemote"));
		getRemoteClipboard.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.DOWN));

		return getRemoteClipboard;
	}

	private Action createRemoteClipboardUpdateAction() {
		final Action setRemoteClipboard = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				sendLocalClipboard();
			}
		};

		setRemoteClipboard.putValue(Action.NAME, "setClipboard");
		setRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("clipboard.setRemote"));
		setRemoteClipboard.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.UP));

		return setRemoteClipboard;
	}

	private void sendLocalClipboard() {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable content = clipboard.getContents(this);

		if (content == null) return;

		try {
			if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				Log.debug("Clipboard contains files");
				clipboard.getAvailableDataFlavors();
				List<File> files = (List) clipboard.getData(DataFlavor.javaFileListFlavor);
				if (files.stream().anyMatch(File::isDirectory)) {
					throw new IOException("directories not supported");
				}
				final long filesSize = files.stream().mapToInt(f -> Math.toIntExact(f.length())).sum();
				// Ok as very few of that (!)
				new Thread(() -> network.setRemoteClipboardFiles(files, filesSize), "setRemoteClipboardFiles").start();
			} else if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                Log.debug("Clipboard contains text: " + text);
				// Ok as very few of that (!)
				new Thread(() -> network.setRemoteClipboardText(text, text.getBytes().length), "setRemoteClipboardText").start();
			}
		} catch (IOException | UnsupportedFlavorException ex) {
			Log.error("Clipboard error " + ex.getMessage());
		}
	}

	/**
	 * Should not block (!)
	 */
	private void sendRemoteClipboardRequest() {
		Log.info("Requesting remote clipboard");
		// Ok as very few of that (!)
		new Thread(network::sendRemoteClipboardRequest, "RemoteClipboardRequest").start();
	}

	private Action createCaptureConfigurationAction() {
		final Action configure = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				JFrame frame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

				final JPanel pane = new JPanel();

				pane.setLayout(new GridLayout(2, 2, 10, 10));

				final JLabel tickLbl = new JLabel(Babylon.translate("tick"));
				tickLbl.setToolTipText(Babylon.translate("tick.tooltip"));

				final JTextField tickTextField = new JTextField();
				tickTextField.setText(String.valueOf(captureEngineConfiguation.getCaptureTick()));

				pane.add(tickLbl);
				pane.add(tickTextField);

				final JLabel grayLevelsLbl = new JLabel(Babylon.translate("grays"));
				final JComboBox<Gray8Bits> grayLevelsCb = new JComboBox<>(Gray8Bits.values());
				grayLevelsCb.setSelectedItem(captureEngineConfiguation.getCaptureQuantization());

				pane.add(grayLevelsLbl);
				pane.add(grayLevelsCb);

				final boolean ok = DialogFactory.showOkCancel(frame, Babylon.translate("capture.settings"), pane, () -> {
                    final String tick = tickTextField.getText();
                    if (tick.isEmpty()) {
                        return Babylon.translate("tick.msg1");
                    }

                    try {
                        Integer.valueOf(tick);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("tick.msg2");
                    }

                    return null;
                });

				if (ok) {
					final CaptureEngineConfiguration configuration = new CaptureEngineConfiguration(Integer.parseInt(tickTextField.getText()),
							(Gray8Bits) grayLevelsCb.getSelectedItem());

					if (!configuration.equals(captureEngineConfiguation)) {
						captureEngineConfiguation = configuration;
						captureEngineConfiguation.persist();

						sendCaptureConfiguration(captureEngineConfiguation);
					}
				}
			}
		};

		configure.putValue(Action.NAME, "configureCapture");
		configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("capture.settings.msg"));
		configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.CAPTURE_SETTINGS));

		return configure;
	}

	/**
	 * Should not block (!)
	 */
	private void sendCaptureConfiguration(final CaptureEngineConfiguration captureEngineConfiguation) {
		// Ok as very few of that (!)
		new Thread(() -> network.sendCaptureConfiguration(captureEngineConfiguation), "CaptureEngineSettingsSender").start();
	}

	private Action createComressionConfigurationAction() {
		final Action configure = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				JFrame frame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());

				final JPanel pane = new JPanel();
				pane.setLayout(new GridLayout(4, 2, 10, 10));

				final JLabel methodLbl = new JLabel(Babylon.translate("compression.method"));
				final JComboBox<CompressionMethod> methodCb = new JComboBox<>(CompressionMethod.values());
				methodCb.setSelectedItem(compressorEngineConfiguation.getMethod());

				pane.add(methodLbl);
				pane.add(methodCb);

				final JLabel useCacheLbl = new JLabel(Babylon.translate("compression.cache.usage"));
				final JCheckBox useCacheCb = new JCheckBox();
				useCacheCb.setSelected(compressorEngineConfiguation.useCache());

				pane.add(useCacheLbl);
				pane.add(useCacheCb);

				final JLabel maxSizeLbl = new JLabel(Babylon.translate("compression.cache.max"));
				maxSizeLbl.setToolTipText(Babylon.translate("compression.cache.max.tooltip"));
				final JTextField maxSizeTf = new JTextField(String.valueOf(compressorEngineConfiguation.getCacheMaxSize()));

				pane.add(maxSizeLbl);
				pane.add(maxSizeTf);

				final JLabel purgeSizeLbl = new JLabel(Babylon.translate("compression.cache.purge"));
				purgeSizeLbl.setToolTipText(Babylon.translate("compression.cache.purge.tooltip"));
				final JTextField purgeSizeTf = new JTextField(String.valueOf(compressorEngineConfiguation.getCachePurgeSize()));

				pane.add(purgeSizeLbl);
				pane.add(purgeSizeTf);

				useCacheCb.addActionListener(ev1 -> {
                    maxSizeLbl.setEnabled(useCacheCb.isSelected());
                    maxSizeTf.setEnabled(useCacheCb.isSelected());
                    purgeSizeLbl.setEnabled(useCacheCb.isSelected());
                    purgeSizeTf.setEnabled(useCacheCb.isSelected());
                });

				maxSizeLbl.setEnabled(useCacheCb.isSelected());
				maxSizeTf.setEnabled(useCacheCb.isSelected());
				purgeSizeLbl.setEnabled(useCacheCb.isSelected());
				purgeSizeTf.setEnabled(useCacheCb.isSelected());

				final boolean ok = DialogFactory.showOkCancel(frame, Babylon.translate("compression.settings"), pane, () -> {
                    final String max = maxSizeTf.getText();
                    if (max.isEmpty()) {
                        return Babylon.translate("compression.cache.max.msg1");
                    }

                    final int _max;

                    try {
                        _max = Integer.valueOf(max);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("compression.cache.max.msg2");
                    }

                    final String purge = purgeSizeTf.getText();
                    if (purge.isEmpty()) {
                        return Babylon.translate("compression.cache.purge.msg1");
                    }

                    final int _purge;

                    try {
                        _purge = Integer.valueOf(purge);
                    } catch (NumberFormatException ex) {
                        return Babylon.translate("compression.cache.purge.msg2");
                    }

                    if (_max <= 0) {
                        return Babylon.translate("compression.cache.max.msg3");
                    }

                    if (_purge <= 0) {
                        return Babylon.translate("compression.cache.purge.msg3");
                    }

                    if (_purge >= _max) {
                        return Babylon.translate("compression.cache.purge.msg4");
                    }

                    return null;
                });

				if (ok) {
					final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration((CompressionMethod) methodCb.getSelectedItem(),
							useCacheCb.isSelected(), Integer.parseInt(maxSizeTf.getText()), Integer.parseInt(purgeSizeTf.getText()));

					if (!configuration.equals(compressorEngineConfiguation)) {
						compressorEngineConfiguation = configuration;
						compressorEngineConfiguation.persist();

						sendCompressorConfiguration(compressorEngineConfiguation);
					}
				}
			}
		};

		configure.putValue(Action.NAME, "configureCompression");
		configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("compression.settings.msg"));
		configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.COMPRESSION_SETTINGS));

		return configure;
	}

	/**
	 * Should not block (!)
	 */
	private void sendCompressorConfiguration(final CompressorEngineConfiguration compressorEngineConfiguration) {
		// Ok as very few of that (!)
		new Thread(() -> network.sendCompressorConfiguration(compressorEngineConfiguration), "CompressorEngineSettingsSender").start();
	}

	private Action createResetAction() {
		final Action configure = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				// Currently making a RESET within the assisted ...
				sendCaptureConfiguration(captureEngineConfiguation);
			}
		};

		configure.putValue(Action.NAME, "resetCapture");
		configure.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("capture.reset"));
		configure.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.RESET_CAPTURE));

		return configure;
	}

	private Action createSwitchLookAndFeelAction() {
		final Action exit = new AbstractAction() {

			public void actionPerformed(ActionEvent ev) {
				final JPopupMenu choices = new JPopupMenu();

				final LookAndFeel current = UIManager.getLookAndFeel();
				choices.add(new JMenuItem(current.getName()));
				choices.addSeparator();

				for (final UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
					if (info.getName().equals(current.getName())) {
						continue;
					}

					final JMenuItem mi = new JMenuItem(info.getName());

					mi.addActionListener(ev1 -> switchLookAndFeel(info));
					choices.add(mi);
				}

				final Point where = MouseInfo.getPointerInfo().getLocation();

				// *** HACK
				// ********************************************************************************************
				final JComponent caller = (JComponent) ev.getSource();

				SwingUtilities.convertPointFromScreen(where, caller);
				choices.show(caller, 0, caller.getHeight());
			}
		};

		exit.putValue(Action.NAME, "lf");
		exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("lnf.switch"));
		exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.LNF));

		return exit;
	}

	private void switchLookAndFeel(UIManager.LookAndFeelInfo lnf) {
		try {
			if (frame != null) {
				UIManager.setLookAndFeel(lnf.getClassName());
				SwingUtilities.updateComponentTreeUI(frame);

				configuration = new AssistantConfiguration(lnf.getClassName());
				configuration.persist();
			}
		} catch (Exception ex) {
			Log.warn("Cound not set the L&F [" + lnf.getName() + "]", ex);
		}
	}

	private class MyDeCompressorEngineListener implements DeCompressorEngineListener {
		/**
		 * Called from within THE de-compressor engine thread => prevBuffer
		 * usage (!)
		 */
		public void onDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
			final Pair<BufferedImage, byte[]> image;

			// synchronized because of the reset onStarting()
			synchronized (prevBufferLOCK) {
				image = capture.createBufferedImage(prevBuffer, prevWidth, prevHeight);

				prevBuffer = image.snd;
				prevWidth = image.fst.getWidth();
				prevHeight = image.fst.getHeight();
			}

			frame.onCaptureUpdated(capture.getId(), image.fst);

			receivedTileCounter.add(capture.getDirtyTileCount(), cacheHits);
			skippedTileCounter.add(capture.getSkipped());
			mergedTileCounter.add(capture.getMerged());

			captureCompressionCounter.add(capture.getDirtyTileCount(), compressionRatio);
		}
	}

	private class MyNetworkAssistantEngineListener implements NetworkAssistantEngineListener {
		public void onReady() {
			frame.onReady();
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public void onHttpStarting(int port) {
			frame.onHttpStarting(port);

			synchronized (prevBufferLOCK) {
				prevBuffer = null;
				prevWidth = -1;
				prevHeight = -1;
			}
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public void onStarting(int port) {
			frame.onStarting(port);
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public void onAccepting(int port) {
			frame.onAccepting(port);
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public boolean onAccepted(Socket connection) {
			return frame.onAccepted(connection);
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public void onConnected(Socket connection) {
			sendCaptureConfiguration(captureEngineConfiguation);
			sendCompressorConfiguration(compressorEngineConfiguation);
		}

		/**
		 * Should not block as called from the network receiving thread (!)
		 */
		public void onByteReceived(int count) {
			receivedBitCounter.add(8 * count);
		}

		public void onIOError(IOException error) {
			frame.onIOError(error);
		}
	}

}
