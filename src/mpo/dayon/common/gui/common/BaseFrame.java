package mpo.dayon.common.gui.common;

import mpo.dayon.assistant.resource.ImageNames;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;

public abstract class BaseFrame extends JFrame
{
	private static final long serialVersionUID = 3844044465771664147L;

	protected ToolBar toolBar;

    protected StatusBar statusBar;

    public BaseFrame()
    {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImage(ImageUtilities.getOrCreateIcon(ImageNames.APP).getImage());

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent ev)
            {
                doExit();
            }
        });
    }

    protected void doExit()
    {
        if (JOptionPane.showConfirmDialog(this,
                                          Babylon.translate("exit.confirm"),
                                          Babylon.translate("exit"),
                                          JOptionPane.OK_CANCEL_OPTION,
                                          JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION)
        {
            Log.info("Bye!");
            System.exit(0);
        }
    }

    protected void setupToolBar(ToolBar toolBar)
    {
        this.toolBar = toolBar;
        add(toolBar, BorderLayout.NORTH);
    }

    protected void setupStatusBar(StatusBar statusBar)
    {
        this.statusBar = statusBar;
        add(statusBar, BorderLayout.SOUTH);
    }

    protected Action createExitAction()
    {
        final Action exit = new AbstractAction()
        {
			private static final long serialVersionUID = -6255123500219118167L;

			public void actionPerformed(ActionEvent ev)
            {
                doExit();
            }
        };

        exit.putValue(Action.NAME, "exit");
        exit.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("exit.dayon"));
        exit.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.EXIT));

        return exit;
    }

    protected static final String HTTP_HOME = "http://dayonhome.sourceforge.net/index.html";

    protected static final String HTTP_SUPPORT = "http://dayonhome.sourceforge.net/support.html";

    protected static final String HTTP_FEEDBACK = "http://dayonhome.sourceforge.net/feedback.html";

    protected Action createShowInfoAction()
    {
        final Action showSystemInfo = new AbstractAction()
        {
			private static final long serialVersionUID = 3696155654667295840L;

			public void actionPerformed(ActionEvent ev)
            {
                final JTextArea props = new JTextArea(SystemUtilities.getSystemPropertiesEx());
                props.setEditable(false);

                final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
                props.setFont(font);

                final JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

                panel.setPreferredSize(new Dimension(500, 250));

                final JLabel info = new JLabel("<html><a href=''>Dayon!</a> : " + Babylon.translate("synopsys") + ".</html>");
                info.setAlignmentX(Component.LEFT_ALIGNMENT);
                //info.setAlignmentX(Component.CENTER_ALIGNMENT);
                info.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        browse(HTTP_HOME);
                    }
                });

                final JLabel support = new JLabel("<html>" + Babylon.translate("support") + " : <a href=''>" + HTTP_SUPPORT + "</a></html>");
                support.setAlignmentX(Component.LEFT_ALIGNMENT);
                //support.setAlignmentX(Component.CENTER_ALIGNMENT);
                support.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        browse(HTTP_SUPPORT);
                    }
                });

                final JLabel feedback = new JLabel("<html>" + Babylon.translate("feedback") + " : <a href=''>" + HTTP_FEEDBACK + "</a></html>");
                feedback.setAlignmentX(Component.LEFT_ALIGNMENT);
                //feedback.setAlignmentX(Component.CENTER_ALIGNMENT);
                feedback.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        browse(HTTP_FEEDBACK);
                    }
                });

                final JScrollPane spane = new JScrollPane(props);
                spane.setAlignmentX(Component.LEFT_ALIGNMENT);

                panel.add(Box.createVerticalStrut(10));
                panel.add(info);
                panel.add(Box.createVerticalStrut(10));
                panel.add(spane);
                panel.add(Box.createVerticalStrut(10));
                panel.add(support);
                panel.add(Box.createVerticalStrut(5));
                panel.add(feedback);

                JOptionPane.showMessageDialog(BaseFrame.this,
                                              panel,
                                              Babylon.translate("system.info"),
                                              JOptionPane.INFORMATION_MESSAGE,
                                              ImageUtilities.getOrCreateIcon(ImageNames.APP_LARGE));

            }
        };

        showSystemInfo.putValue(Action.NAME, "showSystemInfo");
        showSystemInfo.putValue(Action.SHORT_DESCRIPTION, Babylon.translate("system.info.show"));
        showSystemInfo.putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.INFO));

        return showSystemInfo;
    }

    private static void browse(String url)
    {
        try
        {
            if (Desktop.isDesktopSupported())
            {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE))
                {
                    desktop.browse(new URI(url));
                }
            }
        }
        catch (Exception ex)
        {
            Log.warn(ex);
        }
    }

    public ToolBar getToolBar()
    {
        return toolBar;
    }
}
