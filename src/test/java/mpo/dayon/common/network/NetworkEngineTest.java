package mpo.dayon.common.network;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void bindsTheFileConnectionToTheMainPeerCertificate() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});

        // when
        engine.rememberMainPeerIdentity();

        // then
        assertTrue(engine.isFileConnectionBound(socketWithPeerCertificate(new byte[]{1, 2, 3})));
        assertFalse(engine.isFileConnectionBound(socketWithPeerCertificate(new byte[]{4, 5, 6})));
    }

    @Test
    void rejectsAFileConnectionWithoutAPeerCertificate() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();

        final SSLSocket unverified = mock(SSLSocket.class);
        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no peer"));
        when(unverified.getSession()).thenReturn(session);

        // when, then
        assertFalse(engine.isFileConnectionBound(unverified));
        assertFalse(engine.isFileConnectionBound(null));
    }

    @Test
    void rejectsAFileConnectionWhenTheMainIdentityIsUnknown() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then
        assertFalse(engine.isFileConnectionBound(socketWithPeerCertificate(new byte[]{1, 2, 3})));
    }

    @Test
    void forgetsTheMainPeerIdentityWhenConnectionsAreClosed() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();
        final SSLSocket fileConnection = socketWithPeerCertificate(new byte[]{1, 2, 3});

        // when
        engine.closeConnections();

        // then
        assertFalse(engine.isFileConnectionBound(fileConnection));
    }

    @Test
    void onlyAcceptsTheSamePeerAfterAnInterruption() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();

        // when
        engine.rememberPeerIdentityForReconnect();

        // then
        assertTrue(engine.hasPeerIdentityForReconnect());
        assertTrue(engine.isExpectedReconnectPeer(socketWithPeerCertificate(new byte[]{1, 2, 3})));
        assertFalse(engine.isExpectedReconnectPeer(socketWithPeerCertificate(new byte[]{4, 5, 6})));
    }

    @Test
    void rejectsAnyPeerWhenNoInterruptedSessionWasRemembered() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then
        assertFalse(engine.hasPeerIdentityForReconnect());
        assertFalse(engine.isExpectedReconnectPeer(socketWithPeerCertificate(new byte[]{1, 2, 3})));
    }

    @Test
    void rejectsAReconnectWithoutAPeerCertificate() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();
        engine.rememberPeerIdentityForReconnect();

        final SSLSocket unverified = mock(SSLSocket.class);
        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no peer"));
        when(unverified.getSession()).thenReturn(session);

        // when, then
        assertFalse(engine.isExpectedReconnectPeer(unverified));
        assertFalse(engine.isExpectedReconnectPeer(null));
    }

    @Test
    void forgetsTheExpectedReconnectPeerWhenConnectionsAreClosed() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();
        engine.rememberPeerIdentityForReconnect();

        // when
        engine.closeConnections();

        // then
        assertFalse(engine.hasPeerIdentityForReconnect());
        assertFalse(engine.isExpectedReconnectPeer(socketWithPeerCertificate(new byte[]{1, 2, 3})));
    }

    private static SSLSocket socketWithPeerCertificate(byte[] encodedCertificate) throws Exception {
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(encodedCertificate);

        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{certificate});

        final SSLSocket socket = mock(SSLSocket.class);
        when(socket.getSession()).thenReturn(session);
        return socket;
    }
}
