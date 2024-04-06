package mpo.dayon.common.gui.common;

import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import javax.swing.*;

import com.dosse.upnp.UPnP;
import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.network.NetworkAssistantEngineConfiguration;
import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.version.Version;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageNames.FINGERPRINT;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;
import static mpo.dayon.common.gui.toolbar.ToolBar.*;
import static mpo.dayon.common.utils.SystemUtilities.*;

public abstract class BaseFrame extends JFrame {

    protected static final String ROLLOVER_ICON = "ROLLOVER_ICON";

    protected static final String SELECTED_ICON = "SELECTED_ICON";

    protected static final String PRESSED_ICON = "PRESSED_ICON";

    protected final transient Object[] okCancelOptions = {translate("cancel"), translate("ok")};

    private static final String HTTP_HOME = "https://github.com/retgal/dayon";

    private static final String HTTP_SUPPORT = "https://retgal.github.io/Dayon/" + translate("support.html");

    private static final String HTTP_FEEDBACK = HTTP_HOME + "/issues";

    private static final String HTTP_LICENSE = "https://raw.githubusercontent.com/RetGal/Dayon/master/debian/copyright";

    private static final String HTTP_PRIVACY = "https://retgal.github.io/Dayon/" + translate("privacy.html");

    private static final String CHAT_URL = "https://meet.jit.si/%s";

    private transient FrameConfiguration configuration;

    private transient Position position;

    private Dimension dimension;

    private FrameType frameType;

    private ToolBar toolBar;

    private StatusBar statusBar;

    private static final JLabel fingerprints = new JLabel();

    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

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
            Log.info("Bye!");
            System.exit(0);
        }
    }

    protected void setFrameType(FrameType frameType) {
        this.frameType = frameType;
        this.configuration = new FrameConfiguration(frameType);
        this.position = new Position(configuration.getX(), configuration.getY());
        this.setLocation(position.getX(), position.getY());
        this.dimension = new Dimension(Math.max(configuration.getWidth(), frameType.getMinWidth()),
                Math.max(configuration.getHeight(), frameType.getMinHeight()));
        this.setSize(dimension.width, dimension.height);
        setTitle(format("Dayon! (%s) %s", translate(frameType.getPrefix()), Version.get()));
    }

    protected void setupToolBar(ToolBar toolBar) {
        float alignmentY = frameType.equals(FrameType.ASSISTANT) ? Component.BOTTOM_ALIGNMENT : Component.CENTER_ALIGNMENT;
        if (FrameType.ASSISTANT.equals(frameType)) {
            // poor man's vertical align top
            fingerprints.setBorder(BorderFactory.createEmptyBorder(0, 10, 35, 0));
        }
        toolBar.add(fingerprints);
        toolBar.addAction(createShowInfoAction(), alignmentY);
        toolBar.addAction(createShowHelpAction(), alignmentY);
        toolBar.addAction(createExitAction(), alignmentY);
        if (FrameType.ASSISTANT.equals(frameType)) {
            toolBar.add(DEFAULT_SPACER);
        }
        add(toolBar, BorderLayout.NORTH);
        toolBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        this.toolBar = toolBar;
    }

    protected void setupStatusBar(StatusBar statusBar) {
        statusBar.add(Box.createHorizontalStrut(10));
        add(statusBar, BorderLayout.SOUTH);
        this.statusBar = statusBar;
        updateInputLocale();
        new Timer(5000, e -> updateInputLocale()).start();
    }

    private void updateInputLocale() {
        String currentKeyboardLayout = InputContext.getInstance().getLocale().toString();
        if (!currentKeyboardLayout.equals(statusBar.getKeyboardLayout())) {
            statusBar.setKeyboardLayout(currentKeyboardLayout);
        }
    }

    protected JButton createButton(Action action) {
        return createButton(action, true);
    }

    protected JButton createButton(Action action, boolean visible) {
        final JButton button = new JButton();
        addButtonProperties(action, button);
        button.setVisible(visible);
        return button;
    }

    protected JToggleButton createToggleButton(Action action) {
        return createToggleButton(action, true);
    }

    protected JToggleButton createToggleButton(Action action, boolean visible) {
        final JToggleButton button = new JToggleButton();
        addButtonProperties(action, button);
        button.setVisible(visible);
        return button;
    }

    protected JToggleButton createToggleButton(Action action, boolean visible, boolean selected) {
        final JToggleButton button = createToggleButton(action, visible);
        button.setSelected(selected);
        return button;
    }

    private void addButtonProperties(Action action, AbstractButton button) {
        button.setMargin(ZERO_INSETS);
        button.setHideActionText(true);
        button.setAction(action);
        button.setFont(DEFAULT_FONT);
        button.setText((String) action.getValue(DISPLAY_NAME));
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
        final Action showSystemInfo = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                final JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
                panel.setPreferredSize(new Dimension(500, 300));

                final JLabel info = new JLabel(composeLabelHtml("Dayon!", translate("synopsys")));
                info.setAlignmentX(Component.LEFT_ALIGNMENT);
                info.addMouseListener(new HomeMouseAdapter());
                info.setCursor(handCursor);
                final JLabel version = new JLabel(composeLabelHtmlWithBuildNumber(translate("version.installed"), Version.get().toString(), getBuildNumber()));
                version.setAlignmentX(Component.LEFT_ALIGNMENT);
                version.addMouseListener(new ReleaseMouseAdapter());
                version.setCursor(handCursor);
                final JLabel latest = new JLabel(composeLabelHtml(translate("version.latest"), Version.get().getLatestRelease()));
                version.setAlignmentX(Component.LEFT_ALIGNMENT);
                latest.addMouseListener(new LatestReleaseMouseAdapter());
                latest.setCursor(handCursor);

                final JTextArea props = new JTextArea(getSystemPropertiesEx());
                props.setEditable(false);
                props.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                final JScrollPane spane = new JScrollPane(props);
                spane.setAlignmentX(Component.LEFT_ALIGNMENT);

                final JButton support = new JButton(translate("support"));
                support.addMouseListener(new SupportMouseAdapter());
                final JButton feedback = new JButton(translate("feedback"));
                feedback.addMouseListener(new FeedbackMouseAdapter());
                final JButton privacy = new JButton(translate("privacy"));
                privacy.addMouseListener(new PrivacyMouseAdapter());
                final JButton license = new JButton(translate("license"));
                license.addMouseListener(new LicenseMouseAdapter());
                final JPanel buttonsPanel = new JPanel();
                buttonsPanel.setLayout(new GridLayout(1, 4));
                buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                buttonsPanel.add(support);
                buttonsPanel.add(feedback);
                buttonsPanel.add(privacy);
                buttonsPanel.add(license);

                panel.add(Box.createVerticalStrut(10));
                panel.add(info);
                panel.add(Box.createVerticalStrut(5));
                panel.add(version);
                if (Version.get().getLatestRelease() != null) {
                    panel.add(Box.createVerticalStrut(5));
                    panel.add(latest);
                }
                panel.add(Box.createVerticalStrut(5));
                panel.add(spane);
                panel.add(Box.createVerticalStrut(5));
                panel.add(buttonsPanel);

                final Object[] options = {translate("ok")};

                JOptionPane.showOptionDialog(BaseFrame.this, panel, translate("system.info"),
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                        getOrCreateIcon(ImageNames.APP_LARGE), options, options[0]);

            }
        };
        showSystemInfo.putValue(Action.SHORT_DESCRIPTION, translate("system.info.show"));
        showSystemInfo.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.INFO));

        return showSystemInfo;
    }

    protected Action createConnectionSettingsAction(Assistant assistant) {
        final Action conf = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame networkFrame = (JFrame) SwingUtilities.getRoot((Component) ev.getSource());
                final Font titleFont = new Font("Sans Serif", Font.BOLD, 14);
                final String custom = "custom";

                final JPanel panel = new JPanel();
                panel.setLayout(new GridBagLayout());

                int gridy = 0;

                final JTextField addressTextField = new JTextField();
                final JTextField portNumberTextField = new JTextField();
                final JCheckBox autoConnectCheckBox = new JCheckBox();
                String currentTokenServer;

                if (frameType.equals(FrameType.ASSISTED)) {
                    final NetworkAssistedEngineConfiguration networkConfiguration = new NetworkAssistedEngineConfiguration();
                    currentTokenServer = networkConfiguration.getTokenServerUrl();
                    final JLabel hostLbl = new JLabel(toUpperFirst(translate("assistant")));
                    hostLbl.setFont(titleFont);
                    panel.add(hostLbl, createGridBagConstraints(gridy++));

                    final JPanel assistantPanel = new JPanel(new GridLayout(4, 2, 10, 0));
                    assistantPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
                    final JLabel addressLbl = new JLabel(translate("connection.settings.assistantIpAddress"));
                    addressTextField.setText(networkConfiguration.getServerName());
                    assistantPanel.add(addressLbl);
                    assistantPanel.add(addressTextField);
                    final JLabel portNumberLbl = new JLabel(translate("connection.settings.assistantPortNumber"));
                    portNumberTextField.setText(format("%d", networkConfiguration.getServerPort()));
                    assistantPanel.add(portNumberLbl);
                    assistantPanel.add(portNumberTextField);
                    autoConnectCheckBox.setText(translate("connection.settings.autoConnect"));
                    autoConnectCheckBox.setSelected(networkConfiguration.isAutoConnect());
                    assistantPanel.add(autoConnectCheckBox);
                    panel.add(assistantPanel, createGridBagConstraints(gridy++));
                } else {
                    final NetworkAssistantEngineConfiguration networkConfiguration = new NetworkAssistantEngineConfiguration();
                    currentTokenServer = networkConfiguration.getTokenServerUrl();
                    final JLabel hostLbl = new JLabel(toUpperFirst(translate("host")));
                    hostLbl.setFont(titleFont);
                    panel.add(hostLbl, createGridBagConstraints(gridy++));

                    final JPanel upnpPanel = new JPanel(new GridLayout(1, 1, 10, 0));
                    upnpPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
                    boolean upnpActive = assistant.isUpnpEnabled();
                    final JLabel upnpStatus = new JLabel(format("<html>%s<br>%s</html>", format(translate(format("connection.settings.upnp.%s", upnpActive)), UPnP.getDefaultGatewayIP()), translate(format("connection.settings.portforward.%s", upnpActive))));
                    upnpPanel.add(upnpStatus);
                    panel.add(upnpPanel, createGridBagConstraints(gridy++));

                    final JPanel portPanel = new JPanel(new GridLayout(1, 2, 10, 0));
                    portPanel.setBorder(BorderFactory.createEmptyBorder(10,0,20,0));
                    final JLabel portNumberLbl = new JLabel(translate("connection.settings.portNumber"));
                    portNumberLbl.setToolTipText(translate("connection.settings.portNumber.tooltip"));
                    portNumberTextField.setText(format("%d", networkConfiguration.getPort()));
                    portPanel.add(portNumberLbl);
                    portPanel.add(portNumberTextField);
                    panel.add(portPanel, createGridBagConstraints(gridy++));
                }

                final JLabel tokenServerLbl = new JLabel(toUpperFirst(translate("token.server")));
                tokenServerLbl.setFont(titleFont);
                panel.add(tokenServerLbl, createGridBagConstraints(gridy++));

                final JPanel tokenPanel = new JPanel(new GridLayout(3, 2, 10, 0));
                tokenPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));

                final ButtonGroup tokenRadioGroup = new ButtonGroup();
                final JRadioButton defaultTokenRadio = new JRadioButton(translate("token.default.server"));
                defaultTokenRadio.setActionCommand("default");
                final JRadioButton customTokenRadio = new JRadioButton(translate("token.custom.server"));
                customTokenRadio.setActionCommand(custom);
                tokenRadioGroup.add(defaultTokenRadio);
                tokenRadioGroup.add(customTokenRadio);
                boolean customTextFieldEditable = false;
                if (currentTokenServer.isEmpty() || currentTokenServer.equals(DEFAULT_TOKEN_SERVER_URL)) {
                    currentTokenServer = "";
                    defaultTokenRadio.setSelected(true);
                } else {
                    customTokenRadio.setSelected(true);
                    customTextFieldEditable = true;
                }

                final JTextField defaultTokenTextField = new JTextField(DEFAULT_TOKEN_SERVER_URL);
                defaultTokenTextField.setEditable(false);
                defaultTokenTextField.setFocusable(false);
                final JTextField customTokenTextField = new JTextField(currentTokenServer);
                customTokenTextField.setEditable(customTextFieldEditable);

                defaultTokenRadio.addActionListener(evt -> {defaultTokenRadio.requestFocus(); customTokenTextField.setEditable(false);});
                customTokenRadio.addActionListener(evt -> {customTokenTextField.requestFocus(); customTokenTextField.setEditable(true);});

                tokenPanel.add(defaultTokenRadio);
                tokenPanel.add(defaultTokenTextField);

                tokenPanel.add(customTokenRadio);
                tokenPanel.add(customTokenTextField);
                panel.add(tokenPanel, createGridBagConstraints(gridy));

                final boolean ok = DialogFactory.showOkCancel(networkFrame, translate("connection.network"), panel, true,
                        () -> validateInputFields(addressTextField, portNumberTextField, tokenRadioGroup, customTokenTextField));

                if (ok) {
                    final String newTokenServerUrl = tokenRadioGroup.getSelection().getActionCommand().equals(custom) &&
                            isValidUrl(customTokenTextField.getText()) ? customTokenTextField.getText() : "";
                    updateSystemProperty(newTokenServerUrl);
                    updateNetworkConfiguration(addressTextField, portNumberTextField, autoConnectCheckBox, newTokenServerUrl);
                }
            }

            private String validateInputFields(JTextField addressTextField, JTextField portNumberTextField, ButtonGroup tokenRadioGroup, JTextField customTokenTextField) {
                if (frameType.equals(FrameType.ASSISTED)) {
                    final String ipAddress = addressTextField.getText();
                    if (ipAddress.isEmpty()) {
                        return translate("connection.settings.emptyIpAddress");
                    } else if (!isValidIpAddressOrHostName(ipAddress)) {
                        return translate("connection.settings.invalidIpAddress");
                    }
                }
                final String portNumber = portNumberTextField.getText();
                if (portNumber.isEmpty()) {
                    return translate("connection.settings.emptyPortNumber");
                } else if (!isValidPortNumber(portNumber)) {
                    return translate("connection.settings.invalidPortNumber");
                } else if (tokenRadioGroup.getSelection().getActionCommand().equals("custom") && !isValidUrl(customTokenTextField.getText())) {
                    return translate("connection.settings.invalidTokenServer");
                }
                return null;
            }

            private void updateNetworkConfiguration(JTextField addressTextField, JTextField portNumberTextField, JCheckBox autoConnectCheckBox, String newTokenServerUrl) {
                if (frameType.equals(FrameType.ASSISTED)) {
                    final NetworkAssistedEngineConfiguration newNetworkConfiguration = new NetworkAssistedEngineConfiguration(
                            addressTextField.getText(), Integer.parseInt(portNumberTextField.getText()), autoConnectCheckBox.isSelected(), newTokenServerUrl);

                    if (!newNetworkConfiguration.equals(new NetworkAssistedEngineConfiguration())) {
                        newNetworkConfiguration.persist();
                    }
                    return;
                }

                final NetworkAssistantEngineConfiguration newNetworkConfiguration = new NetworkAssistantEngineConfiguration(
                        Integer.parseInt(portNumberTextField.getText()), newTokenServerUrl);

                NetworkAssistantEngineConfiguration networkConfiguration = new NetworkAssistantEngineConfiguration();
                if (!newNetworkConfiguration.equals(networkConfiguration)) {
                    final NetworkAssistantEngine networkEngine = assistant.getNetworkEngine();
                    networkEngine.manageRouterPorts(networkConfiguration.getPort(), newNetworkConfiguration.getPort());
                    newNetworkConfiguration.persist();
                    networkEngine.reconfigure(newNetworkConfiguration);
                }
            }

            private void updateSystemProperty(String newTokenServerUrl) {
                if (newTokenServerUrl.isEmpty()) {
                    System.clearProperty("dayon.custom.tokenServer");
                    return;
                }
                System.setProperty("dayon.custom.tokenServer", newTokenServerUrl);
            }

            private String toUpperFirst(String text) {
                return Pattern.compile("^.").matcher(text).replaceFirst(m -> m.group().toUpperCase());
            }
        };
        conf.putValue(Action.SHORT_DESCRIPTION, translate("connection.settings"));
        conf.putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.NETWORK_SETTINGS));
        return conf;
    }

    private GridBagConstraints createGridBagConstraints(int gridy) {
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

    private String composeLabelHtml(String label, String url) {
        return format("<html>%s : <a href=''>%s</a></html>", label, url);
    }

    private String composeLabelHtmlWithBuildNumber(String label, String url, String buildNumber) {
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

    private static void browse(String url) {
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
            } else if (Desktop.isDesktopSupported()) {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                } else if (isFlat()) {
                    new ProcessBuilder(FLATPAK_BROWSER, uri.toString()).start();
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
        return fingerprints;
    }

    protected void clearFingerprints() {
        fingerprints.setText(null);
        fingerprints.setIcon(null);
        fingerprints.setCursor(null);
    }

    public void setFingerprints(String hash) {
        fingerprints.setIcon(getOrCreateIcon(FINGERPRINT));
        fingerprints.setToolTipText(translate("startChat"));
        fingerprints.setText(format("%s ", hash));
        fingerprints.setFont(DEFAULT_FONT);
        fingerprints.addMouseListener(new ChatMouseAdapter());
        fingerprints.setCursor(handCursor);
    }

    private static class FeedbackMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_FEEDBACK);
        }
    }

    private static class LicenseMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_LICENSE);
        }
    }

    private static class PrivacyMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_PRIVACY);
        }
    }

    private static class HomeMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_HOME);
        }
    }

    private static class ReleaseMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(Version.RELEASE_LOCATION + Version.get());
        }
    }

    private static class LatestReleaseMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(Version.RELEASE_LOCATION + Version.get().getLatestRelease());
        }
    }

    private static class SupportMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_SUPPORT);
        }
    }

    private static class ChatMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(format(CHAT_URL, fingerprints.getText().trim().replace(":", "-")));
        }
    }
}
