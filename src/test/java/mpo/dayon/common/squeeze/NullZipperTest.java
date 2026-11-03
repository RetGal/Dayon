package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NullZipperTest {

    private final NullZipper zipper = new NullZipper();

    @Test
    void zipCopiesTheContent() {
        // given
        final MemByteBuffer unzipped = new MemByteBuffer();
        unzipped.writes(1, 2, 3);

        // when
        final MemByteBuffer zipped = zipper.zip(unzipped);

        // then
        assertArrayEquals(new byte[] { 1, 2, 3 }, zipped.getInternal());
        assertArrayEquals(new byte[] { 1, 2, 3 }, unzipped.getInternal());
    }

    @Test
    void unzipCopiesTheContent() {
        // given
        final MemByteBuffer zipped = new MemByteBuffer();
        zipped.writes(4, 5, 6);

        // when
        final MemByteBuffer unzipped = zipper.unzip(zipped);

        // then
        assertArrayEquals(new byte[] { 4, 5, 6 }, unzipped.getInternal());
        assertArrayEquals(new byte[] { 4, 5, 6 }, zipped.getInternal());
    }

    @Test
    void zipAndUnzipAnEmptyBuffer() {
        // given
        final MemByteBuffer empty = new MemByteBuffer();

        // when, then
        assertEquals(0, zipper.zip(empty).size());
        assertEquals(0, zipper.unzip(empty).size());
    }
}
