package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.BaseFrame;
import mpo.dayon.common.gui.statusbar.StatusBar;
import mpo.dayon.common.gui.toolbar.ToolBar;
import mpo.dayon.common.version.Version;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AssistedFrame extends BaseFrame
{
    private AssistedFrameConfiguration configuration;

    public AssistedFrame(AssistedFrameConfiguration configuration)
    {
        this.configuration = configuration;

        setTitle("Dayon! (" + Babylon.translate("assisted") + ") " + Version.get());

        setupToolBar(createToolBar());
        setupStatusBar(createStatusBar());

        this.setLocation(configuration.getX(), configuration.getY());
        this.setSize(configuration.getWidth(), configuration.getHeight());

        addWindowListener(new WindowAdapter()
        {
            public void windowOpened(WindowEvent ev)
            {
                addComponentListener(new ComponentAdapter()
                {
                    public void componentResized(ComponentEvent ev)
                    {
                        onSizeUpdated(getWidth(), getHeight());
                    }

                    public void componentMoved(ComponentEvent ev)
                    {
                        onLocationUpdated(getX(), getY());
                    }
                });
            }
        });

        onReady();
    }

    private void onLocationUpdated(int x, int y)
    {
        configuration = new AssistedFrameConfiguration(x, y, configuration.getWidth(), configuration.getHeight());
        configuration.persist(false);
    }

    private void onSizeUpdated(int width, int height)
    {
        configuration = new AssistedFrameConfiguration(configuration.getX(), configuration.getY(), width, height);
        configuration.persist(false);
    }

    private ToolBar createToolBar()
    {
        final ToolBar toolbar = new ToolBar();

        toolbar.addAction(createShowInfoAction());
        toolbar.addGlue();
        toolbar.addAction(createExitAction());

        return toolbar;
    }

    private StatusBar createStatusBar()
    {
        final StatusBar statusBar = new StatusBar();

        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.add(Box.createHorizontalStrut(10));

        return statusBar;
    }

    public void onReady()
    {
        statusBar.setMessage(Babylon.translate("ready"));
    }

    public void onHttpConnecting(NetworkAssistedEngineConfiguration configuration)
    {
        statusBar.setMessage(Babylon.translate("http.handshake", configuration.getServerName(), configuration.getServerPort()));
    }

    public void onConnecting(NetworkAssistedEngineConfiguration configuration)
    {
        statusBar.setMessage(Babylon.translate("connecting", configuration.getServerName(), configuration.getServerPort()));
    }

    public void onConnected()
    {
        statusBar.setMessage(Babylon.translate("connected"));
    }
}
