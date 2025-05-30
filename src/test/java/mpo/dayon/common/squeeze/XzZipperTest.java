package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XzZipperTest {

    @Test
    void zipAndUnzip() throws IOException {
        // given
        int star = 42;
        MemByteBuffer origin = MemByteBuffer.acquire();
        origin.write(star);
        XzZipper zipper = new XzZipper();
        // when
        final MemByteBuffer unzipped = zipper.unzip(zipper.zip(origin));
        // then
        assertEquals(new String(origin.getInternal(), UTF_8), new String(unzipped.getInternal(), UTF_8));
    }
}