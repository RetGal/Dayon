package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PackBitsRunLengthEncoderTest {

    private final PackBitsRunLengthEncoder encoder = new PackBitsRunLengthEncoder();

    @Test
    void roundTripsRunsAtPacketBoundaries() {
        for (int length : new int[] { 0, 1, 2, 3, 4, 127, 128, 129, 130, 131, 132, 260, 261, 262 }) {
            MemByteBuffer input = new MemByteBuffer();
            input.fill(length, 0xA5);
            assertRoundTrip(input);
        }
    }

    @Test
    void roundTripsMixedByteValues() {
        Random random = new Random(42);
        MemByteBuffer input = new MemByteBuffer();
        for (int i = 0; i < 4096; i++) {
            input.write(random.nextInt(256));
        }
        assertRoundTrip(input);
    }

    @Test
    void preservesInputWhenEncodingInPlace() {
        MemByteBuffer inputAndOutput = new MemByteBuffer();
        inputAndOutput.writes(1, 1, 1, 2, 3, 3, 4);

        MemByteBuffer expected = new MemByteBuffer();
        encoder.runLengthEncode(expected, new MemByteBuffer(inputAndOutput.getInternal()));

        encoder.runLengthEncode(inputAndOutput, inputAndOutput);

        assertArrayEquals(expected.getInternal(),
                Arrays.copyOfRange(inputAndOutput.getInternal(), 7, inputAndOutput.size()));
    }

    private void assertRoundTrip(MemByteBuffer input) {
        MemByteBuffer encoded = new MemByteBuffer();
        encoder.runLengthEncode(encoded, input);
        MemByteBuffer decoded = new MemByteBuffer();
        encoder.runLengthDecode(decoded, encoded);
        assertArrayEquals(input.getInternal(), decoded.getInternal());
    }
}
