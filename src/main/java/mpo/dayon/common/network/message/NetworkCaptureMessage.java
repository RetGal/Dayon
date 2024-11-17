package mpo.dayon.common.network.message;

import java.io.*;

import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.utils.UnitUtilities;

public class NetworkCaptureMessage extends NetworkMessage {
	private final int id;

	private final CompressionMethod compressionMethod;

	private final CompressorEngineConfiguration compressionConfiguration;

	private final MemByteBuffer payload;

	public NetworkCaptureMessage(int id, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
			MemByteBuffer payload) {
		this.id = id;
		this.compressionMethod = compressionMethod;
		this.compressionConfiguration = compressionConfiguration;
		this.payload = payload;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.CAPTURE;
	}

	public int getId() {
		return id;
	}

	public CompressionMethod getCompressionMethod() {
		return compressionMethod;
	}

	public CompressorEngineConfiguration getCompressionConfiguration() {
		return compressionConfiguration;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		if (compressionConfiguration == null) {
			return 11 + payload.size(); // type (byte) + capture-id (int) +
										// compression (byte) +
										// configuration-marker (byte) + len
										// (int) + data (byte[])
		}
		return 10 + 11 + payload.size(); // type (byte) + capture-id (int) +
										 // compression (byte) +
										 // configuration (???) + len
										 // (int) + data (byte[])
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		// debugging info - might need it before decompressing the payload (!)
		out.writeInt(id);
		// allows for decompressing on the other side ...
		marshallEnum(out, compressionMethod);
		out.writeByte(compressionConfiguration != null ? 1 : 0);
		if (compressionConfiguration != null) {
			new NetworkCompressorConfigurationMessage(compressionConfiguration).marshall(out);
		}
		out.writeInt(payload.size());
		out.write(payload.getInternal(), 0, payload.size());
	}

	public static NetworkCaptureMessage unmarshall(ObjectInputStream in) throws IOException {
		final int id = in.readInt();
		final CompressionMethod compressionMethod = unmarshallEnum(in, CompressionMethod.class);

		final CompressorEngineConfiguration compressionConfiguration;

		if (in.readByte() == 1) {
			NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
			compressionConfiguration = NetworkCompressorConfigurationMessage.unmarshall(in).getConfiguration();
		} else {
			compressionConfiguration = null;
		}

		final int len = in.readInt();
		final byte[] data = new byte[len];
		int offset = 0;
		int count;
		while ((count = in.read(data, offset, len - offset)) > 0) {
			offset += count;
		}
		return new NetworkCaptureMessage(id, compressionMethod, compressionConfiguration, new MemByteBuffer(data));
	}

	public MemByteBuffer getPayload() {
		return payload;
	}

	public String toString() {
		return String.format("[id:%d][%s]", id, UnitUtilities.toBitSize(8d * payload.size()));
	}
}