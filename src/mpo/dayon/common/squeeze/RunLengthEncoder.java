package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

import java.io.IOException;

public abstract class RunLengthEncoder
{
    public abstract void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) throws IOException;

    public abstract void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) throws IOException;

}
