package mpo.dayon.common.network.transport;

import mpo.dayon.common.security.CustomTrustManager;

import javax.net.ssl.SSLSession;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;

/**
 * An ordered, encrypted byte stream carrying a Dayon! session: TLS over TCP or over an ICE component.
 */
public interface SessionTransport extends Closeable {

    // decrypted inbound stream
    InputStream getInputStream() throws IOException;

    // outbound stream, encrypted on write
    OutputStream getOutputStream() throws IOException;

    boolean isConnected();

    // peer address, or {@code null} if not yet known
    InetAddress getRemoteAddress();

    default String getRemoteHostAddress() {
        final InetAddress address = getRemoteAddress();
        return address == null ? "?" : address.getHostAddress();
    }

    SSLSession getSslSession();

    // SHA-256 fingerprint of peer certificate, or {@code null} if unknown
    default byte[] getPeerCertificateFingerprint() {
        return CustomTrustManager.calculatePeerCertificateFingerprint(getSslSession());
    }

    default String getFingerprints(String side) throws NoSuchAlgorithmException, CertificateEncodingException, javax.net.ssl.SSLPeerUnverifiedException {
        return CustomTrustManager.calculateFingerprints(getSslSession(), side);
    }

    // opens the file transfer channel: a new TLS connection for TCP (initiating side only), a second multiplexed stream for ICE
    SessionTransport openFileChannel() throws IOException;

    @Override
    void close();
}
