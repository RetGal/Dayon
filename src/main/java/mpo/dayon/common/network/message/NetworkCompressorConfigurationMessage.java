package mpo.dayon.common.network.message;

import java.io.*;

import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.squeeze.CompressionMethod;

public class NetworkCompressorConfigurationMessage extends NetworkMessage {
	private final CompressorEngineConfiguration configuration;

	public NetworkCompressorConfigurationMessage(CompressorEngineConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.COMPRESSOR_CONFIGURATION;
	}

	public CompressorEngineConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		return 11; // type (byte) + method (byte) + useCase (byte) + max (int) +
					// purge (int)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		marshallEnum(out, configuration.getMethod());
		out.writeByte(configuration.useCache() ? 1 : 0);
		out.writeInt(configuration.getCacheMaxSize());
		out.writeInt(configuration.getCachePurgeSize());
	}

	public static NetworkCompressorConfigurationMessage unmarshall(ObjectInputStream in) throws IOException {
		final CompressionMethod method = unmarshallEnum(in, CompressionMethod.class);
		final boolean useCase = in.readByte() == 1;
		final int maxSize = in.readInt();
		final int purgeSize = in.readInt();
		return new NetworkCompressorConfigurationMessage(new CompressorEngineConfiguration(method, useCase, maxSize, purgeSize));
	}

	public String toString() {
		return String.format("[configuration:%s]", configuration.getMethod());
	}

}