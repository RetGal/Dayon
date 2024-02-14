package mpo.dayon.common.gui.common;

import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import javax.swing.*;

import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import mpo.dayon.common.version.Version;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.toolbar.ToolBar.*;
import static mpo.dayon.common.utils.SystemUtilities.*;

public abstract class BaseFrame extends JFrame {

    private static final String HTTP_HOME = "https://github.com/retgal/dayon";

    private static final String HTTP_SUPPORT = "https://retgal.github.io/Dayon/" + translate("support.html");

    private static final String HTTP_FEEDBACK = HTTP_HOME + "/issues";

    protected static final Object[] OK_CANCEL_OPTIONS = {translate("cancel"), translate("ok")};

    private transient FrameConfiguration configuration;

    private transient Position position;

    private Dimension dimension;

    private FrameType frameType;

    private ToolBar toolBar;

    private StatusBar statusBar;

    private Locale currentLocale;

    protected BaseFrame() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
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
        if (JOptionPane.showOptionDialog(this, translate("exit.confirm"), translate("exit"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, OK_CANCEL_OPTIONS,
                OK_CANCEL_OPTIONS[1]) == 1) {
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
        setTitle();
        new Timer(5000, e -> setTitle()).start();
    }

    private void setTitle() {
        Locale newLocale = InputContext.getInstance().getLocale();
        if (newLocale != currentLocale) {
            currentLocale = newLocale;
            setTitle(format("Dayon! (%s) %s %s", translate(frameType.getPrefix()), Version.get(), currentLocale != null ? currentLocale.toString() : ""));
        }
    }

    protected void setupToolBar(ToolBar toolBar) {
        float alignmentY = frameType.equals(FrameType.ASSISTANT) ? Component.BOTTOM_ALIGNMENT : Component.CENTER_ALIGNMENT;
        toolBar.add(DEFAULT_SPACER);
        if (FrameType.ASSISTANT.equals(frameType)) {
            // poor man's vertical align top
            toolBar.getFingerprints().setBorder(BorderFactory.createEmptyBorder(0, 10, 35, 0));
        }
        toolBar.add(toolBar.getFingerprints());
        toolBar.addAction(createShowInfoAction(), alignmentY);
        toolBar.addAction(createShowHelpAction(), alignmentY);
        toolBar.addAction(createExitAction(), alignmentY);
        toolBar.add(DEFAULT_SPACER);
        add(toolBar, BorderLayout.NORTH);
        toolBar.setBorder(null);
        this.toolBar = toolBar;
    }

    protected void setupStatusBar(StatusBar statusBar) {
        statusBar.add(Box.createHorizontalStrut(10));
        add(statusBar, BorderLayout.SOUTH);
        this.statusBar = statusBar;
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

    private void addButtonProperties(Action action, AbstractButton button) {
        button.setMargin(ZERO_INSETS);
        button.setHideActionText(true);
        button.setAction(action);
        button.setFont(DEFAULT_FONT);
        button.setText((String) action.getValue("DISPLAY_NAME"));
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
        exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.EXIT));
        return exit;
    }

    private Action createShowInfoAction() {
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

                final JLabel info = new JLabel(composeLabelHtml("Dayon!", translate("synopsys")));
                info.setAlignmentX(Component.LEFT_ALIGNMENT);
                info.addMouseListener(new HomeMouseAdapter());

                final JLabel version = new JLabel(composeLabelHtmlWithBuildNumber(translate("version.installed"), Version.get().toString(), getBuildNumber()));
                version.setAlignmentX(Component.LEFT_ALIGNMENT);
                version.addMouseListener(new ReleaseMouseAdapter());

                final JLabel latest = new JLabel(composeLabelHtml(translate("version.latest"), Version.get().getLatestRelease()));
                version.setAlignmentX(Component.LEFT_ALIGNMENT);
                latest.addMouseListener(new LatestReleaseMouseAdapter());

                final JLabel support = new JLabel(composeLabelHtml(translate("support"), HTTP_SUPPORT));
                support.setAlignmentX(Component.LEFT_ALIGNMENT);
                support.addMouseListener(new SupportMouseAdapter());

                final JLabel feedback = new JLabel(composeLabelHtml(translate("feedback"), HTTP_FEEDBACK));
                feedback.setAlignmentX(Component.LEFT_ALIGNMENT);
                feedback.addMouseListener(new FeedbackMouseAdapter());

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

                final Object[] options = {translate("ok")};

                JOptionPane.showOptionDialog(BaseFrame.this, panel, translate("system.info"),
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                        ImageUtilities.getOrCreateIcon(ImageNames.APP_LARGE), options, options[0]);

            }
        };
        showSystemInfo.putValue(Action.SHORT_DESCRIPTION, translate("system.info.show"));
        showSystemInfo.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.INFO));

        return showSystemInfo;
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
                browse(SystemUtilities.getQuickStartURI(translate("quickstart.html"), frameType.getPrefix()));
            }
        };
        showHelp.putValue(Action.SHORT_DESCRIPTION, translate("help"));
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

    public void setFingerprints(String fingerprints) {
        toolBar.setFingerprints(fingerprints);
    }

    private static class FeedbackMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            browse(HTTP_FEEDBACK);
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
}
