package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class NullRunLengthEncoderTest {

    @Test
    void encodeAndDecodeAreSimplePassThroughs() {
        // given
        NullRunLengthEncoder encoder = new NullRunLengthEncoder();
        MemByteBuffer encoded = new MemByteBuffer();
        MemByteBuffer decoded = new MemByteBuffer();
        byte[] capture = {5, 5, 6, 7};

        // when
        encoder.runLengthEncode(encoded, new MemByteBuffer(capture));
        encoder.runLengthDecode(decoded, encoded);

        // then
        assertArrayEquals(capture, decoded.getInternal());
    }
}
