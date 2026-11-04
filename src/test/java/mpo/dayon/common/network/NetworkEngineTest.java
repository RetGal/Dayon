package mpo.dayon.common.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class NetworkEngineTest {

    private static class TestNetworkEngine extends NetworkEngine {
        @Override
        protected void fireOnClipboardReceived() {
            // visible for testing
        }

        @Override
        protected void fireOnIOError(IOException error) {
            // visible for testing
        }
    }

    @Test
    void nullToZeroStr() {
        // when, then
        assertEquals("0", NetworkEngine.nullToZeroStr(null));
        assertEquals("42", NetworkEngine.nullToZeroStr("42"));
    }

    @Test
    void sendingWithoutAConnectionIsANoOp() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then - must not throw
        assertDoesNotThrow(() -> engine.sendClipboardText("unicorn"));
        assertDoesNotThrow(() -> engine.sendClipboardFiles(Collections.emptyList(), 0, ""));
    }

    @Test
    void selfTestFailsWithoutAPublicIp() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then
        assertFalse(engine.selfTest(null, 8080));
        assertEquals(Boolean.FALSE, NetworkEngine.isOwnPortAccessible.get());
    }

    @Test
    void createInputStreamRequiresAConnection() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then
        assertThrows(IOException.class, engine::createInputStream);
    }

    @Test
    void closesUnusedConnections() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then - must not throw
        assertDoesNotThrow(engine::closeConnections);
    }

    @Test
    void initializesTheIceAgentOnceAndFailsWithoutStreams() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when
        engine.initializeIceAgent();
        final Object firstAgent = engine.iceAgent;
        engine.initializeIceAgent();
        final String sdp = engine.getLocalSdpDesc();

        // then
        assertNotNull(firstAgent, "the ICE agent should have been created");
        assertSame(firstAgent, engine.iceAgent, "the ICE agent should only be created once");
        assertNull(sdp, "an ICE agent without streams cannot create an SDP description");
    }

    @Test
    void handlesAnIoError() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then - must not throw
        assertDoesNotThrow(() -> engine.handleIOException(new IOException("snafu")));
        engine.cancelling.set(true);
        assertDoesNotThrow(() -> engine.handleIOException(new IOException("snafu")));
    }
}
