package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.capture.CaptureEngine;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.capture.RobotCaptureFactory;
import mpo.dayon.assisted.compressor.CompressorEngine;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.control.RobotNetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngine;
import mpo.dayon.assisted.mouse.MouseEngineConfiguration;
import mpo.dayon.assisted.network.NetworkAssistedEngine;
import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.gui.common.DialogFactory;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkCaptureConfigurationMessage;
import mpo.dayon.common.network.message.NetworkCaptureConfigurationMessageHandler;
import mpo.dayon.common.network.message.NetworkCompressorConfigurationMessage;
import mpo.dayon.common.network.message.NetworkCompressorConfigurationMessageHandler;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class Assisted
{
    private AssistedFrame frame;

    private NetworkAssistedEngineConfiguration configuration;

    private volatile CaptureEngine captureEngine;

    private volatile CompressorEngine compressorEngine;


    public Assisted()
    {
    }

    public void configure()
    {
        final String lnf = SystemUtilities.getDefaultLookAndFeel();
        try
        {
            UIManager.setLookAndFeel(lnf);
        }
        catch (Exception ex)
        {
            Log.warn("Cound not set the [" + lnf + "] L&F!", ex);
        }
    }

    public void start() throws IOException
    {
        frame = new AssistedFrame(new AssistedFrameConfiguration());

        FatalErrorHandler.attachFrame(frame);

        frame.setVisible(true);

        configuration = new NetworkAssistedEngineConfiguration();

        final String ip = SystemUtilities.getStringProperty(null, "dayon.assistant.ipAddress", null);
        final Integer port = SystemUtilities.getIntProperty(null, "dayon.assistant.portNumber", -1);

        if (ip == null)
        {
            if (!requestConnectionSettings())
            {
                Log.info("Bye!");
                System.exit(0);
            }
        }
        else // JNLP startup (!)
        {
            final NetworkAssistedEngineConfiguration xconfiguration = new NetworkAssistedEngineConfiguration(ip, port);
            if (!xconfiguration.equals(configuration))
            {
                configuration = xconfiguration;
                configuration.persist();
            }
        }

        Log.info("Configuration " + configuration);

        // -------------------------------------------------------------------------------------------------------------
        // HTTP request to notify the assistant we're ready and starting : i.e., JNLP stuff is done.

        frame.onHttpConnecting(configuration);

        Log.info("[HTTP-handshake] Sending the /hello request...");

        try
        {
            final URL url = new URL("http://" + configuration.getServerName() + ":" + configuration.getServerPort() + "/hello");
            final URLConnection conn = url.openConnection();

            conn.getInputStream();

            // Currently do not care about the response : returning a 404 response (!)
            throw new RuntimeException("[HTTP-handshake] Missing expected /hello response!");
        }
        catch (FileNotFoundException expected)
        {
            // ignored
        }

        Log.info("[HTTP-handshake] Expected response (404) received.");

        // -------------------------------------------------------------------------------------------------------------

        final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler =
                new NetworkCaptureConfigurationMessageHandler()
                {
                    /**
                     * Should not block as called from the network incoming message thread (!)
                     */
                    public void handleConfiguration(NetworkEngine engine, NetworkCaptureConfigurationMessage configuration)
                    {
                        onCaptureEngineConfigured(engine, configuration);
                        frame.onConnected();
                    }
                };

        final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler =
                new NetworkCompressorConfigurationMessageHandler()
                {
                    /**
                     * Should not block as called from the network incoming message thread (!)
                     */
                    public void handleConfiguration(NetworkEngine engine, NetworkCompressorConfigurationMessage configuration)
                    {
                        onCompressorEngineConfigured(engine, configuration);
                        frame.onConnected();
                    }
                };

        final NetworkControlMessageHandler controlHandler = new RobotNetworkControlMessageHandler();

        frame.onConnecting(configuration);

        final NetworkAssistedEngine networkEngine =
                new NetworkAssistedEngine(captureConfigurationHandler,
                                          compressorConfigurationHandler,
                                          controlHandler);

        networkEngine.configure(configuration);
        networkEngine.start();
        networkEngine.sendHello();
    }

    private boolean requestConnectionSettings()
    {
        final JPanel pane = new JPanel();

        pane.setLayout(new GridLayout(2, 2, 10, 10));

        final JLabel assistantIpAddress = new JLabel(Babylon.translate("connection.settings.assistantIpAddress"));
        final JTextField assistantIpAddressTextField = new JTextField();
        assistantIpAddressTextField.setText(configuration.getServerName());

        pane.add(assistantIpAddress);
        pane.add(assistantIpAddressTextField);

        final JLabel assistantPortNumberLbl = new JLabel(Babylon.translate("connection.settings.assistantPortNumber"));
        final JTextField assistantPortNumberTextField = new JTextField();
        assistantPortNumberTextField.setText(String.valueOf(configuration.getServerPort()));

        pane.add(assistantPortNumberLbl);
        pane.add(assistantPortNumberTextField);

        final boolean ok = DialogFactory.showOkCancel(frame, Babylon.translate("connection.settings"), pane, new DialogFactory.Validator()
        {
            public String validate()
            {
                final String ipAddress = assistantIpAddressTextField.getText();
                if (ipAddress.isEmpty())
                {
                    return Babylon.translate("connection.settings.emptyIpAddress");
                }

                final String portNumber = assistantPortNumberTextField.getText();
                if (portNumber.isEmpty())
                {
                    return Babylon.translate("connection.settings.emptyPortNumer");
                }

                try
                {
                    Integer.valueOf(portNumber);
                }
                catch (NumberFormatException ex)
                {
                    return Babylon.translate("connection.settings.invalidPortNumber");
                }

                return null;
            }
        });

        if (ok)
        {
            final NetworkAssistedEngineConfiguration xconfiguration = new NetworkAssistedEngineConfiguration(assistantIpAddressTextField.getText(), Integer.valueOf(assistantPortNumberTextField.getText()));
            if (!xconfiguration.equals(configuration))
            {
                configuration = xconfiguration;
                configuration.persist();
            }
        }

        return ok;
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onCaptureEngineConfigured(NetworkEngine engine, NetworkCaptureConfigurationMessage configuration)
    {
        final CaptureEngineConfiguration captureEngineConfiguration = configuration.getConfiguration();

        Log.info("Capture configuration received " + captureEngineConfiguration);

        if (captureEngine != null)
        {
            captureEngine.reconfigure(captureEngineConfiguration);
            return;
        }

        // First time we receive a configuration from the assistant (!)

        // Setup the mouse engine (no need before I guess)

        final MouseEngine mouseEngine = new MouseEngine();

        mouseEngine.configure(new MouseEngineConfiguration());
        mouseEngine.addListener((NetworkAssistedEngine) engine);
        mouseEngine.start();

        captureEngine = new CaptureEngine(new RobotCaptureFactory());
        captureEngine.configure(captureEngineConfiguration);

        if (compressorEngine != null)
        {
            captureEngine.addListener(compressorEngine);
        }

        captureEngine.start();
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    private void onCompressorEngineConfigured(NetworkEngine engine, NetworkCompressorConfigurationMessage configuration)
    {
        final CompressorEngineConfiguration compressorEngineConfiguration = configuration.getConfiguration();

        Log.info("Compressor configuration received " + compressorEngineConfiguration);

        if (compressorEngine != null)
        {
            compressorEngine.reconfigure(compressorEngineConfiguration);
            return;
        }

        compressorEngine = new CompressorEngine();

        compressorEngine.configure(compressorEngineConfiguration);
        compressorEngine.addListener((NetworkAssistedEngine) engine);
        compressorEngine.start(1);

        if (captureEngine != null)
        {
            captureEngine.addListener(compressorEngine);
        }
    }

}
