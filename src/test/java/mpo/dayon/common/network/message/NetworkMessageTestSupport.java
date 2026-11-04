package mpo.dayon.common.network.message;

import java.io.*;

/**
 * Test support for network message (de-)serialisation tests.
 */
public class NetworkMessageTestSupport {

    private NetworkMessageTestSupport() {
    }

    /**
     * Calls {@code marshall} on a NoHeaderObjectOutputStream and returns the raw bytes.
     */
     public static byte[] marshallToBytes(MessageMarshaller marshaller) {
         try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             NoHeaderObjectOutputStream out = new NoHeaderObjectOutputStream(baos)) {
             marshaller.marshall(out);
             out.flush();
             return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes bytes via DataOutputStream.
     */
    public static byte[] writeBytes(DataWriter writer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writer.write(out);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Executes the given callback with a no-header ObjectInputStream.
     * Any IOException thrown during construction or close is wrapped in an UncheckedIOException.
     */
    public static void withNoHeaderInput(byte[] data, ObjectInputStreamConsumer consumer) {
        ObjectInputStream in = null;
        try {
            in = new NoHeaderObjectInputStream(new ByteArrayInputStream(data));
            consumer.accept(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // closing silently
                }
            }
        }
    }

    @FunctionalInterface
    public interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    public interface MessageMarshaller {
        void marshall(ObjectOutputStream out) throws IOException;
    }

    @FunctionalInterface
    public interface ObjectInputStreamConsumer {
        void accept(ObjectInputStream in) throws IOException;
    }

    /**
     * ObjectOutputStream that does not write a stream header.
     */
    static class NoHeaderObjectOutputStream extends ObjectOutputStream {
        NoHeaderObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() {
            // no header
        }
    }

    /**
     * ObjectInputStream that does not expect a stream header.
     */
    static class NoHeaderObjectInputStream extends ObjectInputStream {
        NoHeaderObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected void readStreamHeader() {
            // no header to read
        }
    }
}
