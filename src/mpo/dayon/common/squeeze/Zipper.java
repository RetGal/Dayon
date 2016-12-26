package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

import java.io.IOException;

abstract class Zipper
{
    public abstract MemByteBuffer zip(MemByteBuffer unzipped) throws IOException;

    public abstract MemByteBuffer unzip(MemByteBuffer zipped) throws IOException;

}
