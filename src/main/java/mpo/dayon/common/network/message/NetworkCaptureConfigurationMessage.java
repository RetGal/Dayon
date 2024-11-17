package mpo.dayon.common.network.message;

import java.io.*;

import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.Gray8Bits;

public class NetworkCaptureConfigurationMessage extends NetworkMessage {
	private final CaptureEngineConfiguration configuration;
	private final boolean monochromePeer;

	public NetworkCaptureConfigurationMessage(CaptureEngineConfiguration configuration, boolean monochromePeer) {
		this.configuration = configuration;
		this.monochromePeer = monochromePeer;
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
		// for backwards compatibility
		if (monochromePeer) {
			return 6;
		}
		return 8; // type (byte) + quantization (byte) + tick (int) + colors (short)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		marshallEnum(out, configuration.getCaptureQuantization());
		out.writeInt(configuration.getCaptureTick());
		// for backwards compatibility
		if (!monochromePeer) {
			out.writeShort(configuration.isCaptureColors() ? 1 : 0);
		}
	}

	public static NetworkCaptureConfigurationMessage unmarshall(ObjectInputStream in) throws IOException {
		final Gray8Bits quantization = unmarshallEnum(in, Gray8Bits.class);
		final int tick = in.readInt();
		final boolean colors = in.readShort() == 1;
		return new NetworkCaptureConfigurationMessage(new CaptureEngineConfiguration(tick, quantization, colors), false);
	}

	public String toString() {
		return String.format("[quantization:%s][tick:%d][colors:%b]", configuration.getCaptureQuantization(), configuration.getCaptureTick(), configuration.isCaptureColors());
	}

}
