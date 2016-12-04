package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.tools.bzip2.CBZip2OutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BZIP2_Zipper extends Zipper
{
    public BZIP2_Zipper()
    {
    }

    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException
    {
        final MemByteBuffer zipped = new MemByteBuffer();

        final OutputStream zip = createBZip2OutputStream(zipped);

        zip.write(unzipped.getInternal(), 0, unzipped.size());
        zip.flush();

        zip.close();

        return zipped;
    }

    private static OutputStream createBZip2OutputStream(MemByteBuffer zipped) throws IOException
    {
        return new CBZip2OutputStream(zipped);
    }

    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException
    {
        final MemByteBuffer unzipped = new MemByteBuffer();

        final InputStream unzip = createBZip2InputStream(zipped);

        final byte[] buffer = new byte[4096];

        int count;
        while ((count = unzip.read(buffer)) > 0)
        {
            unzipped.write(buffer, 0, count);
        }

        unzip.close();

        return unzipped;
    }

    private static InputStream createBZip2InputStream(MemByteBuffer zipped) throws IOException
    {
        return new CBZip2InputStream(new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size()));
    }

}