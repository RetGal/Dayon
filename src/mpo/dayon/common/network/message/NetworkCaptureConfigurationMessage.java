package mpo.dayon.common.network.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.Gray8Bits;

public class NetworkCaptureConfigurationMessage extends NetworkMessage {
	private final CaptureEngineConfiguration configuration;

	public NetworkCaptureConfigurationMessage(CaptureEngineConfiguration configuration) {
		this.configuration = configuration;
	}

	public NetworkMessageType getType() {
		return NetworkMessageType.CAPTURE_CONFIGURATION;
	}

	public CaptureEngineConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	public int getWireSize() {
		return 6; // type (byte) + quantization (byte) + tick (int)
	}

	public void marshall(DataOutputStream out) throws IOException {
		marshallEnum(out, NetworkMessageType.class, getType());

		marshallEnum(out, Gray8Bits.class, configuration.getCaptureQuantization());
		out.writeInt(configuration.getCaptureTick());
	}

	public static NetworkCaptureConfigurationMessage unmarshall(DataInputStream in) throws IOException {
		final Gray8Bits quantization = unmarshallEnum(in, Gray8Bits.class);
		final int tick = in.readInt();

		return new NetworkCaptureConfigurationMessage(new CaptureEngineConfiguration(tick, quantization));
	}

	public String toString() {
		return String.format("[quantization:%s] [tick:%d]", configuration.getCaptureQuantization(), configuration.getCaptureTick());
	}

}
