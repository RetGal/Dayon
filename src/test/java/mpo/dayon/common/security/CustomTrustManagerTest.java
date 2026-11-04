package mpo.dayon.common.security;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class CustomTrustManagerTest {

    @Test
    void hasNoAcceptedIssuers() {
        // given
        CustomTrustManager trustManager = new CustomTrustManager();

        // when
        X509Certificate[] issuers = trustManager.getAcceptedIssuers();

        // then
        assertNotNull(issuers);
        assertEquals(0, issuers.length);
    }

    @Test
    void trustsAnyClientAndServerCertificate() {
        // given
        CustomTrustManager trustManager = new CustomTrustManager();

        // when, then - we trust our individually generated, self-signed certificates
        assertDoesNotThrow(() -> trustManager.checkClientTrusted(null, "RSA"));
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(null, "RSA"));
    }

    @Test
    void initializesATls13SslContextInCompatibilityMode() throws Exception {
        // when
        SSLContext sslContext = CustomTrustManager.initSslContext(true);

        // then
        assertNotNull(sslContext);
        assertEquals("TLSv1.3", sslContext.getProtocol());
        assertNotNull(sslContext.getSocketFactory());
    }

    @Test
    void calculatesNoFingerprintsWithoutASession() throws Exception {
        // when, then
        assertNull(CustomTrustManager.calculateFingerprints(null, "NetworkAssistedEngine"));
        assertNull(CustomTrustManager.calculateFingerprints(null, "NetworkAssistantEngine"));
    }
}
