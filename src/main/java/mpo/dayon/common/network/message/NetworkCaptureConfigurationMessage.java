package mpo.dayon.common.network.message;

import java.io.*;

import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.Gray8Bits;

public class NetworkCaptureConfigurationMessage extends NetworkMessage {
	private final CaptureEngineConfiguration configuration;

	public NetworkCaptureConfigurationMessage(CaptureEngineConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
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
	@Override
    public int getWireSize() {
		return 8; // type (byte) + quantization (byte) + tick (int) + colors (short)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		marshallEnum(out, configuration.getCaptureQuantization());
		out.writeInt(configuration.getCaptureTick());
		out.writeShort(configuration.isCaptureColors() ? 1 : 0);
	}

	public static NetworkCaptureConfigurationMessage unmarshall(ObjectInputStream in) throws IOException {
		final Gray8Bits quantization = unmarshallEnum(in, Gray8Bits.class);
		final int tick = in.readInt();
		return new NetworkCaptureConfigurationMessage(new CaptureEngineConfiguration(tick, quantization,in.readShort() == 1));
	}

	public String toString() {
		return String.format("[quantization:%s] [tick:%d] [colors:%b]", configuration.getCaptureQuantization(), configuration.getCaptureTick(), configuration.isCaptureColors());
	}

}
