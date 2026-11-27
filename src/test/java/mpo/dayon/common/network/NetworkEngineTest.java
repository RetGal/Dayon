package mpo.dayon.common.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import mpo.dayon.common.network.transport.SessionTransport;
import mpo.dayon.common.network.transport.TcpSessionTransport;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertDoesNotThrow(engine::sendClipboardTransferComplete);
    }

    @Test
    void sendingFilesWithoutAFileConnectionReportsFailure() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when, then
        assertFalse(engine.sendClipboardFiles(Collections.singletonList(new File("unicorn.txt")), 0, ""));
    }

    @Test
    void notifiesThePeerWhenAClipboardTransferCannotSendAnyFile() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.sender = mock(NetworkSender.class);

        // when
        engine.sendClipboardTransferComplete();

        // then
        verify(engine.sender).ping();
    }

    @Test
    void disablesNagleWithoutTouchingTheSocketBuffers() throws IOException {
        // given
        final SSLSocket socket = mock(SSLSocket.class);

        // when
        TcpSessionTransport.of(socket);

        // then - setting the buffers explicitly would disable TCP autotuning
        verify(socket).setTcpNoDelay(true);
        verify(socket, never()).setSendBufferSize(anyInt());
        verify(socket, never()).setReceiveBufferSize(anyInt());
    }

    @Test
    void disablingNagleFailsGracefully() throws IOException {
        // given
        final SSLSocket socket = mock(SSLSocket.class);
        doThrow(new SocketException("not supported")).when(socket).setTcpNoDelay(true);

        // when, then
        assertDoesNotThrow(() -> TcpSessionTransport.of(socket));
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
        assertDoesNotThrow((Executable) engine::closeConnections);
    }

    @Test
    void initializesTheIceAgentOnceAndFailsWithoutStreams() {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();

        // when
        engine.initializeIceAgent(true);
        final Object firstAgent = engine.iceAgent;
        engine.initializeIceAgent(true);
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

        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no peer"));
        final SSLSocket unverifiedSocket = mock(SSLSocket.class);
        when(unverifiedSocket.getSession()).thenReturn(session);
        final SessionTransport unverified = TcpSessionTransport.of(unverifiedSocket);

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
        final SessionTransport fileConnection = socketWithPeerCertificate(new byte[]{1, 2, 3});

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

        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no peer"));
        final SSLSocket unverifiedSocket = mock(SSLSocket.class);
        when(unverifiedSocket.getSession()).thenReturn(session);
        final SessionTransport unverified = TcpSessionTransport.of(unverifiedSocket);

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

    @Test
    void keepsTheReconnectIdentityAndCancellingFlagBetweenReconnectAttempts() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();
        engine.rememberPeerIdentityForReconnect();
        engine.cancelling.set(true);

        // when
        engine.closeConnections(false);

        // then
        assertTrue(engine.hasPeerIdentityForReconnect());
        assertTrue(engine.isExpectedReconnectPeer(socketWithPeerCertificate(new byte[]{1, 2, 3})));
        assertTrue(engine.cancelling.get(), "a reconnect attempt must not reset the cancelling flag");
    }

    @Test
    void finalCleanupForgetsTheReconnectIdentityAndCancellingFlag() throws Exception {
        // given
        final TestNetworkEngine engine = new TestNetworkEngine();
        engine.connection = socketWithPeerCertificate(new byte[]{1, 2, 3});
        engine.rememberMainPeerIdentity();
        engine.rememberPeerIdentityForReconnect();
        engine.cancelling.set(true);

        // when
        engine.closeConnections(true);

        // then
        assertFalse(engine.hasPeerIdentityForReconnect());
        assertFalse(engine.cancelling.get());
    }

    @Test
    void decodesIceDescriptionsOfOlderAssistants() throws IOException {
        // older assistants did not URL encode the description, so the RVS turned '+' into ' '
        assertEquals(">>>?", NetworkEngine.decodeSdp("Pj4 Pw=="));
        assertEquals(">>>?", NetworkEngine.decodeSdp("Pj4+Pw==\n"));
        assertThrows(IOException.class, () -> NetworkEngine.decodeSdp("not*base64"));
    }

    @Test
    void appendsTheUrlEncodedIceDescription() {
        assertEquals("q?a=1&ice=Pj4%2BPw%3D%3D", NetworkEngine.appendIceParam("q?a=1", "Pj4+Pw=="));
        assertEquals("q?a=1", NetworkEngine.appendIceParam("q?a=1", null));
    }

    private static SessionTransport socketWithPeerCertificate(byte[] encodedCertificate) throws Exception {
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(encodedCertificate);

        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{certificate});

        final SSLSocket socket = mock(SSLSocket.class);
        when(socket.getSession()).thenReturn(session);
        return TcpSessionTransport.of(socket);
    }

    @Test
    void parsesTurnServerWithHostnameAndPortOnly() {
        // when
        final List<NetworkEngine.TurnServer> servers = NetworkEngine.parseTurnServers("turn.example.com:3478");

        // then
        assertEquals(1, servers.size());
        assertTurnServer(servers.get(0), "turn.example.com", 3478, null, null);
    }

    @Test
    void parsesTurnServerWithIpv4AndCredentials() {
        // when
        final List<NetworkEngine.TurnServer> servers = NetworkEngine.parseTurnServers("203.0.113.5:3478:user:secret");

        // then
        assertEquals(1, servers.size());
        assertTurnServer(servers.get(0), "203.0.113.5", 3478, "user", "secret");
    }

    @Test
    void parsesTurnServerWithBracketedIpv6() {
        // when
        final List<NetworkEngine.TurnServer> servers = NetworkEngine.parseTurnServers("[2001:db8::1]:5349:user:secret");

        // then
        assertEquals(1, servers.size());
        assertTurnServer(servers.get(0), "2001:db8::1", 5349, "user", "secret");
        assertEquals("[2001:db8::1]:5349", servers.get(0).toString());
    }

    @Test
    void keepsColonsInTheTurnPassword() {
        // when
        final List<NetworkEngine.TurnServer> servers = NetworkEngine.parseTurnServers("[::1]:3478:user:pa:ss:word");

        // then
        assertTurnServer(servers.get(0), "::1", 3478, "user", "pa:ss:word");
    }

    @Test
    void parsesMultipleTurnServersAndSkipsInvalidOnes() {
        // when
        final List<NetworkEngine.TurnServer> servers = NetworkEngine.parseTurnServers(
                " turn.example.com:3478 , 2001:db8::1:3478, ,[2001:db8::2]:3478:u:p,nope");

        // then
        assertEquals(2, servers.size());
        assertTurnServer(servers.get(0), "turn.example.com", 3478, null, null);
        assertTurnServer(servers.get(1), "2001:db8::2", 3478, "u", "p");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "turn.example.com",
            "turn.example.com:",
            "turn.example.com:abc",
            "turn.example.com:0",
            "turn.example.com:65536",
            "2001:db8::1:3478",
            "[2001:db8::1]",
            "[2001:db8::1]3478",
            "[]:3478",
            ":3478"
    })
    void rejectsInvalidTurnServerEntries(String turns) {
        // when, then
        assertTrue(NetworkEngine.parseTurnServers(turns).isEmpty());
    }

    private static void assertTurnServer(NetworkEngine.TurnServer server, String host, int port, String username, String password) {
        assertEquals(host, server.host);
        assertEquals(port, server.port);
        assertEquals(username, server.username);
        assertEquals(password, server.password);
    }
}
