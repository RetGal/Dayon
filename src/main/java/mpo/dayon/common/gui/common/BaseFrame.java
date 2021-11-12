package mpo.dayon.common.gui.common;

import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputContext;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutionException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.SdpUtils;
import mpo.dayon.common.version.Version;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.event.KeyEvent.VK_CAPS_LOCK;
import static java.lang.String.format;
import static javax.swing.SwingUtilities.invokeLater;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.FrameType.ASSISTANT;
import static mpo.dayon.common.gui.common.FrameType.ASSISTED;
import static mpo.dayon.common.gui.common.ImageNames.FINGERPRINT;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.gui.toolbar.ToolBar.*;
import static mpo.dayon.common.log.LogAppender.cleanup;
import static mpo.dayon.common.utils.SystemUtilities.*;

public abstract class BaseFrame extends JFrame {

    protected static final String ROLLOVER_ICON = "ROLLOVER_ICON";

    protected static final String SELECTED_ICON = "SELECTED_ICON";

    private static final String PRESSED_ICON = "PRESSED_ICON";

    protected final transient Object[] okCancelOptions = {translate("cancel"), translate("ok")};

    private static final String HTTP_HOME = "https://github.com/retgal/dayon";

    protected static final String HTTP_SUPPORT = "https://retgal.github.io/Dayon/" + translate("support.html");

    private static final String HTTP_FEEDBACK = HTTP_HOME + "/issues";

    private static final String HTTP_LICENSE = "https://raw.githubusercontent.com/RetGal/Dayon/master/debian/copyright";

    private static final String HTTP_PRIVACY = "https://retgal.github.io/Dayon/" + translate("privacy.html");

    private static final String CHAT_URL = "https://kmeet.infomaniak.com/%s#config.subject=%s&config.disableDeepLinking=true";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build();

    private static final String CUSTOM = "custom";

    private static final JLabel FINGERPRINTS = new JLabel();

    private JPanel biometricPanel;

    private static final MouseAdapter CHAT_MOUSE_ADAPTER = new ChatMouseAdapter();

    private transient FrameConfiguration configuration;

    private transient Position position;

    private Dimension dimension;

    private FrameType frameType;

    private ToolBar toolBar;

    private StatusBar statusBar;

    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    private transient Action preExitAction;

    private String[] tokenServerUrlsFromYaml;

    protected BaseFrame() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(getOrCreateIcon(ImageNames.APP).getImage());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ev) {
                doExit();
            }
        });
        addSizeAndPositionListener();
    }

    private void doExit() {
        if (JOptionPane.showOptionDialog(this, translate("exit.confirm"), translate("exit"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, okCancelOptions,
                okCancelOptions[1]) == 1) {
            if (preExitAction != null) {
                preExitAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
            }
            Log.info("Bye!");
            cleanup();
            SdpUtils.releaseSdpFactory();
            System.exit(0);
        }
    }

    protected void setFrameType(FrameType frameType) {
        this.frameType = frameType;
        setupWindow();
        setTitle(format("Fensterkitt Support App %s", Version.get()));
    }

    private void setupWindow() {
        configuration = new FrameConfiguration(frameType);
        setupDimension();
        final Rectangle maximumWindowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        position = new Position(configuration.getX() + dimension.width < maximumWindowBounds.width ? configuration.getX() : (maximumWindowBounds.width - dimension.width) / 2,
                 configuration.getY() + dimension.height < maximumWindowBounds.height ? configuration.getY() : (maximumWindowBounds.height - dimension.height) / 2);
        setSize(dimension.width, dimension.height);
        setLocation(position.getX(), position.getY());
    }

    void setupDimension() {
        if (frameType.equals(ASSISTED)) {
            int surplus = Runtime.version().version().get(0) > 17 && ("wayland").equals(System.getProperty("xdg.session.type")) ? 40 : 0;
            dimension = new Dimension(frameType.getMinWidth(), frameType.getMinHeight() + surplus);
            setResizable(false);
        } else {
            dimension = new Dimension(Math.max(configuration.getWidth(), frameType.getMinWidth()),
                    Math.max(configuration.getHeight(), frameType.getMinHeight()));
        }
    }

    protected void setupToolBar(ToolBar toolBar, Action toolbarAction) {
        if (ASSISTANT.equals(frameType)) {
            // noop
        } else {
            toolBar.add(FINGERPRINTS);
            toolBar.add(addAction(createExitAction()));
        }
        add(toolBar, BorderLayout.NORTH);
        toolBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        this.toolBar = toolBar;
    }

    protected void setupStatusBar(StatusBar statusBar) {
        statusBar.setTokenserverIndicator(getCurrentTokenServer().isBlank() && tokenServerUrlsFromYaml.length == 0);
        Timer statusBarTimer;
        statusBar.add(Box.createHorizontalStrut(10));
        add(statusBar, BorderLayout.SOUTH);
        this.statusBar = statusBar;
        updateInputLocale();
        updateCapsLockState();
        statusBarTimer = new Timer(3000, e -> {
            updateInputLocale();
            updateCapsLockState();
        });
        statusBarTimer.start();
    }

    private JButton addAction(Action action) {
        return addAction(action, 5);
    }

    private JButton addAction(Action action, int borderSize) {
        final JButton button = new JButton();
        // let jbutton focus and rollover behavior behave as if it were inside a ToolBar
        button.addChangeListener(event -> button.setContentAreaFilled(button.isFocusOwner() || button.getModel().isRollover() || button.getModel().isPressed()));

        addSmallButtonProperties(action, button, borderSize);
        add(button);
        return button;
    }

    private static void addSmallButtonProperties(Action action, AbstractButton button, Integer borderSize) {
        button.setMargin(ZERO_INSETS);
        button.setBorder(BorderFactory.createEmptyBorder(borderSize, borderSize, borderSize, borderSize));
        button.setHideActionText(true);
        button.setAction(action);
        button.setFont(DEFAULT_FONT);
        button.setFocusable(false);
        button.setContentAreaFilled(false);
    }

    private void updateInputLocale() {
        String currentKeyboardLayout = InputContext.getInstance().getLocale().toString();
        if (!currentKeyboardLayout.equals(statusBar.getKeyboardLayout())) {
            statusBar.setKeyboardLayout(currentKeyboardLayout);
        }
    }

    private void updateCapsLockState() {
        boolean currentCapsLockState = Toolkit.getDefaultToolkit().getLockingKeyState(VK_CAPS_LOCK);
        if (currentCapsLockState != statusBar.isCapsLockOn()) {
            statusBar.setCapsLockIndicator(currentCapsLockState);
        }
    }

    protected void setTokenServerUrlsFromYaml(String[] tokenServerUrlsFromYaml) {
        this.tokenServerUrlsFromYaml = tokenServerUrlsFromYaml;
    }

    public void resetConnectionIndicators() {
        statusBar.resetPortStateIndicator();
        statusBar.resetPeerStateIndicator();
    }

    protected static JButton createButton(Action action) {
        return createButton(action, true);
    }

    protected static JButton createButton(Action action, boolean visible) {
        final JButton button = new JButton();
        addButtonProperties(action, button);
        button.setVisible(visible);
        return button;
    }

    protected static JToggleButton createToggleButton(Action action) {
        return createToggleButton(action, true);
    }

    protected static JToggleButton createToggleButton(Action action, boolean visible) {
        final JToggleButton button = new JToggleButton();
        addButtonProperties(action, button);
        button.setVisible(visible);
        return button;
    }

    protected static JToggleButton createToggleButton(Action action, boolean visible, boolean selected) {
        final JToggleButton button = createToggleButton(action, visible);
        button.setSelected(selected);
        return button;
    }

    private static void addButtonProperties(Action action, AbstractButton button) {
        button.setMargin(ZERO_INSETS);
        button.setHideActionText(true);
        button.setAction(action);
        button.setFont(DEFAULT_FONT);
        button.setRolloverIcon((Icon) action.getValue(ROLLOVER_ICON));
        button.setPressedIcon((Icon) action.getValue(PRESSED_ICON));
        button.setSelectedIcon((Icon) action.getValue(SELECTED_ICON));
        button.setFocusable(false);
        button.setDisabledIcon(null);
        button.setSelected(false);
    }

    private Action createExitAction() {
        final Action exit = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                doExit();
            }
        };
        exit.putValue(Action.SHORT_DESCRIPTION, translate("exit.dayon"));
        exit.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.EXIT));
        return exit;
    }

    private Action createShowInfoAction() {
        JLabel latestVersion = new JLabel();
        final Action showSystemInfo = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                invokeLater(() -> showSystemInfoDialog(latestVersion));
            }
        };
        showSystemInfo.putValue(Action.SHORT_DESCRIPTION, translate("system.info.show"));
        showSystemInfo.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.INFO));
        new LatestVersionLabelUpdater(latestVersion).execute();
        return showSystemInfo;
    }

    private void showSystemInfoDialog(JLabel latestVersion) {
        final JPanel panel = createSystemInfoPanel(latestVersion);
        final Object[] options = {translate("ok")};
        JOptionPane.showOptionDialog(this, panel, translate("system.info"),
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                getOrCreateIcon(ImageNames.APP_LARGE), options, options[0]);
    }

    private JPanel createSystemInfoPanel(JLabel latestVersion) {
        final EmptyBorder marginLeft = new EmptyBorder(0, 2, 0, 0);
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.setPreferredSize(new Dimension(500, 300));

        final JLabel info = createLinkLabel(composeLabelHtml("Dayon!", translate("synopsys")), HTTP_HOME, marginLeft);
        final JLabel version = createLinkLabel(
                composeLabelHtmlWithBuildNumber(translate("version.installed"), Version.get().toString(), getBuildNumber()),
                Version.RELEASE_LOCATION + Version.get(), marginLeft);
        latestVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        latestVersion.setBorder(marginLeft);
        latestVersion.addMouseListener(new UrlMouseAdapter(Version.RELEASE_LOCATION + Version.get().getLatestRelease()));
        latestVersion.setCursor(handCursor);

        final JScrollPane sysProps = createSystemPropertiesScrollPane();
        final JPanel buttonsPanel = createInfoButtonsPanel();

        panel.add(Box.createVerticalStrut(10));
        panel.add(info);
        panel.add(Box.createVerticalStrut(5));
        panel.add(version);
        panel.add(Box.createVerticalStrut(5));
        panel.add(latestVersion);
        panel.add(Box.createVerticalStrut(5));
        panel.add(sysProps);
        panel.add(Box.createVerticalStrut(5));
        panel.add(buttonsPanel);
        return panel;
    }

    private JLabel createLinkLabel(String labelHtml, String url, EmptyBorder marginLeft) {
        JLabel label = new JLabel(labelHtml);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(marginLeft);
        label.addMouseListener(new UrlMouseAdapter(url));
        label.setCursor(handCursor);
        return label;
    }

    private static JScrollPane createSystemPropertiesScrollPane() {
        final JTextArea props = new JTextArea(getSystemPropertiesEx());
        props.setEditable(false);
        props.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        final JScrollPane spane = new JScrollPane(props);
        spane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return spane;
    }

    private static JPanel createInfoButtonsPanel() {
        final JButton support = new JButton(translate("support"));
        support.addMouseListener(new UrlMouseAdapter(HTTP_SUPPORT));
        final JButton feedback = new JButton(translate("feedback"));
        feedback.addMouseListener(new UrlMouseAdapter(HTTP_FEEDBACK));
        final JButton privacy = new JButton(translate("privacy"));
        privacy.addMouseListener(new UrlMouseAdapter(HTTP_PRIVACY));
        final JButton license = new JButton(translate("license"));
        license.addMouseListener(new UrlMouseAdapter(HTTP_LICENSE));
        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new GridLayout(1, 4));
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonsPanel.add(support);
        buttonsPanel.add(feedback);
        buttonsPanel.add(privacy);
        buttonsPanel.add(license);
        return buttonsPanel;
    }

    private String getCurrentTokenServer() {
        if (ASSISTED.equals(frameType)) {
            return new NetworkAssistedEngineConfiguration().getTokenServerUrl();
        }
        return null;
    }

    private static int addAssistedConfiguration(JPanel panel, int gridy, Font titleFont, JTextField addressTextField, JTextField portNumberTextField, JCheckBox autoConnectCheckBox) {
        final NetworkAssistedEngineConfiguration networkConfiguration = new NetworkAssistedEngineConfiguration();
        JLabel hostLabel = new JLabel(toUpperFirst(translate("assistant")));
        hostLabel.setFont(titleFont);
        panel.add(hostLabel, createGridBagConstraints(gridy++));

        JPanel assistantPanel = new JPanel(new GridLayout(4, 2, 10, 0));
        assistantPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        addressTextField.setText(networkConfiguration.getServerName());
        portNumberTextField.setText(format("%d", networkConfiguration.getServerPort()));
        autoConnectCheckBox.setSelected(networkConfiguration.isAutoConnect());

        assistantPanel.add(new JLabel(translate("connection.settings.assistantIpAddress")));
        assistantPanel.add(addressTextField);
        assistantPanel.add(new JLabel(translate("connection.settings.assistantPortNumber")));
        assistantPanel.add(portNumberTextField);
        assistantPanel.add(new JLabel(translate("connection.settings.autoConnect")));
        assistantPanel.add(autoConnectCheckBox);
        panel.add(assistantPanel, createGridBagConstraints(gridy++));
        return gridy;
    }

    private static String toUpperFirst(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String validateTokenServerVersion(String tokenServerVersion) {
        if (tokenServerVersion != null) {
            String[] parts = tokenServerVersion.split("\\.");
            if (parts.length == 3) {
                int major = Integer.parseInt(parts[1]);
                int minor = Integer.parseInt(parts[2]);
                if (major < 1 || minor < 6) {
                    return translate("connection.settings.outdatedTokenServer", tokenServerVersion);
                }
                return null;
            }
        }
        return translate("connection.settings.invalidTokenServer");
    }

    private static GridBagConstraints createGridBagConstraints(int gridy) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = gridy;
        return gc;
    }

    private void addSizeAndPositionListener() {
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

    private static String composeLabelHtml(String label, String url) {
        return format("<html>%s : <a href=''>%s</a></html>", label, url);
    }

    private static String composeLabelHtmlWithBuildNumber(String label, String url, String buildNumber) {
        if (buildNumber.isEmpty()) {
            return composeLabelHtml(label, url);
        }
        return format("<html>%s : <a href=''>%s</a> (build %s)</html>", label, url, buildNumber);
    }

    private Action createShowHelpAction() {
        final Action showHelp = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                browse(getQuickStartURI(translate("quickstart.html"), frameType.getPrefix()));
            }
        };
        showHelp.putValue(Action.SHORT_DESCRIPTION, translate("help"));
        showHelp.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.HELP));
        return showHelp;
    }

    protected static void browse(String url) {
        try {
            browse(new URI(url));
        } catch (URISyntaxException ex) {
            Log.warn(ex);
        }
    }

    private static void browse(URI uri) {
        try {
            if (isSnapped()) {
                new ProcessBuilder(getSnapBrowserCommand(), uri.toString()).start();
            } else {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri);
                } else if (isFlat()) {
                    new ProcessBuilder(FLATPAK_BROWSER, uri.toString()).start();
                } else {
                    final String URL = uri.toString();
                    new ProcessBuilder("/bin/sh", "-c", String.format("xdg-open %s || sensible-browser %s || x-www-browser %s || open %s", URL, URL, URL, URL)).start();
                }
            }
        } catch (IOException ex) {
            Log.warn(ex.getMessage());
        }
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

    protected StatusBar getStatusBar() {
        return statusBar;
    }

    protected static JLabel getFingerprints() {
        return FINGERPRINTS;
    }

    protected void clearFingerprints() {
        if (biometricPanel != null) {
            biometricPanel.setVisible(false);
        }
        FINGERPRINTS.setText(null);
        FINGERPRINTS.setIcon(null);
        FINGERPRINTS.setCursor(null);
        FINGERPRINTS.removeMouseListener(CHAT_MOUSE_ADAPTER);
    }

    public void setFingerprints(String hash) {
        if (biometricPanel != null) {
            biometricPanel.setVisible(true);
        }
        FINGERPRINTS.setIcon(getOrCreateIcon(FINGERPRINT));
        FINGERPRINTS.setToolTipText(translate("startChat"));
        FINGERPRINTS.setText(hash + " ");
        FINGERPRINTS.setFont(DEFAULT_FONT);
        FINGERPRINTS.addMouseListener(CHAT_MOUSE_ADAPTER);
        FINGERPRINTS.setCursor(handCursor);
    }

    protected void setPreExistAction(Action exitAction) {
        preExitAction = exitAction;
    }

    private static class LatestVersionLabelUpdater extends SwingWorker<String, Void> {
        private final JLabel latestVersion;

        private LatestVersionLabelUpdater(JLabel latestVersion) {
            this.latestVersion = latestVersion;
        }

        @Override
        protected String doInBackground() {
            return Version.get().getLatestRelease();
        }

        @Override
        protected void done() {
            try {
                String latest = get();
                if (latest != null) {
                    latestVersion.setText(composeLabelHtml(translate("version.latest"), latest));
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.warn("Swallowed", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class UrlMouseAdapter extends MouseAdapter {
        private final String url;

        UrlMouseAdapter(String url) {
            this.url = url;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            browse(url);
        }
    }

    private static class ChatMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            String meetingName = FINGERPRINTS.getText().trim().replace(":", "-");
            browse(format(CHAT_URL, meetingName, meetingName));
        }
    }

    public void onClipboardSending() {}

    public void onClipboardSent() {}
}
