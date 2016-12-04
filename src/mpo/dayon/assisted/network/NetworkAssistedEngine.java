package mpo.dayon.assisted.network;

import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineListener;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.net.Socket;

public class NetworkAssistedEngine
        extends NetworkEngine
        implements Configurable<NetworkAssistedEngineConfiguration>,
                   CompressorEngineListener,
                   MouseEngineListener
{
    private NetworkAssistedEngineConfiguration configuration;

    private final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler;

    private final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler;

    private final NetworkControlMessageHandler controlHandler;

    private final Thread receiver;

    private Socket connection;

    private DataOutputStream out;

    private DataInputStream in;

    private NetworkSender sender;

    public NetworkAssistedEngine(NetworkCaptureConfigurationMessageHandler captureConfigurationHandler,
                                 NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler,
                                 NetworkControlMessageHandler controlHandler)
    {
        this.captureConfigurationHandler = captureConfigurationHandler;
        this.compressorConfigurationHandler = compressorConfigurationHandler;
        this.controlHandler = controlHandler;

        this.receiver = new Thread(new RunnableEx()
        {
            protected void doRun() throws Exception
            {
                NetworkAssistedEngine.this.receivingLoop();
            }
        }, "NetworkReceiver");
    }

    public void configure(@Nullable NetworkAssistedEngineConfiguration configuration)
    {
        this.configuration = configuration;
    }

    public void start() throws IOException
    {
        Log.info("Connecting to [" + configuration.getServerName() + "][" + configuration.getServerPort() + "]...");

        connection = new Socket(configuration.getServerName(), configuration.getServerPort());

        out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(connection.getInputStream()));

        sender = new NetworkSender(out); // the active part (!)
        sender.start(1);

        receiver.start();
    }

    private void receivingLoop() throws IOException
    {
        while (true)
        {
            NetworkMessage.unmarshallMagicNumber(in);

            final NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);

            switch (type)
            {
                case CAPTURE_CONFIGURATION:
                {
                    final NetworkCaptureConfigurationMessage configuration = NetworkCaptureConfigurationMessage.unmarshall(in);
                    captureConfigurationHandler.handleConfiguration(NetworkAssistedEngine.this, configuration);
                    break;
                }

                case COMPRESSOR_CONFIGURATION:
                {
                    final NetworkCompressorConfigurationMessage configuration = NetworkCompressorConfigurationMessage.unmarshall(in);
                    compressorConfigurationHandler.handleConfiguration(NetworkAssistedEngine.this, configuration);
                    break;
                }

                case MOUSE_CONTROL:
                {
                    final NetworkMouseControlMessage message = NetworkMouseControlMessage.unmarshall(in);
                    controlHandler.handleMessage(this, message);
                    break;
                }

                case KEY_CONTROL:
                {
                    final NetworkKeyControlMessage message = NetworkKeyControlMessage.unmarshall(in);
                    controlHandler.handleMessage(this, message);
                    break;
                }

                default:
                    throw new IOException("Unsupported message type [" + type + "]!");
            }
        }
    }

    /**
     * The first message being sent to the assistant (e.g., version identification).
     */
    public void sendHello()
    {
        if (sender != null)
        {
            sender.sendHello();
        }
    }

    /**
     * May block (!)
     * <p/>
     * We're receiving a fully compressed (and ready to send over the network) capture.
     */
    public void onCompressed(Capture capture,
                             CompressionMethod compressionMethod,
                             @Nullable CompressorEngineConfiguration compressionConfiguration,
                             MemByteBuffer compressed)
    {
        if (sender != null)
        {
            sender.sendCapture(capture, compressionMethod, compressionConfiguration, compressed);
        }
    }

    /**
     * May block (!)
     */
    public boolean onLocationUpdated(Point location)
    {
        if (sender != null)
        {
            return sender.sendMouseLocation(location);
        }
        return true;
    }
}
