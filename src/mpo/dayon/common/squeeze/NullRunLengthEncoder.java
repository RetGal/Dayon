package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

import java.io.IOException;

public class NullRunLengthEncoder extends RunLengthEncoder
{
    public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) throws IOException
    {
        out.write(capture.getInternal(), 0, capture.size());
    }

    public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) throws IOException
    {
        out.write(encoded.getInternal(), 0, encoded.size());
    }
}
