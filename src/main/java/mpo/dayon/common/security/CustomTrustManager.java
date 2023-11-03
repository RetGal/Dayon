package mpo.dayon.common.security;

import mpo.dayon.common.log.Log;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.*;

import static java.lang.String.format;
import static java.lang.System.getProperty;

public class CustomTrustManager implements X509TrustManager {
	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[]{};
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) {
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) {
	}

	@java.lang.SuppressWarnings("squid:S6437") // pro forma password, without security relevance
	public static SSLContext initSslContext(boolean compatibilityMode) throws NoSuchAlgorithmException, IOException, KeyManagementException {
		String keyStorePass = "spasspass";
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			final Path keystorePath = Paths.get(new File(getProperty("dayon.home"), "keystore.jks").getAbsolutePath());
			if (compatibilityMode || ! keystorePath.toFile().exists()) {
				keyStore.load(CustomTrustManager.class.getResourceAsStream("/trust/X509"), keyStorePass.toCharArray());
			} else {
				keyStore.load(Files.newInputStream(keystorePath), keyStorePass.toCharArray());
			}
			kmf.init(keyStore, keyStorePass.toCharArray());
		} catch (KeyStoreException | CertificateException | UnrecoverableKeyException e) {
			Log.error("Fatal, can not init encryption", e);
			throw new UnsupportedOperationException(e);
		}
		SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
		sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());
		return sslContext;
	}

	public static String calculateFingerprints(SSLSession session, String side) throws NoSuchAlgorithmException,
			CertificateEncodingException, SSLPeerUnverifiedException {
		if (session != null && session.getSessionContext() != null) {
			return side.equals("NetworkAssistedEngine") ? format("%s:%s", calculateFingerprint(session.getPeerCertificates()[0]), calculateFingerprint(session.getLocalCertificates()[0])) :
					format("%s:%s",calculateFingerprint(session.getLocalCertificates()[0]), calculateFingerprint(session.getPeerCertificates()[0]));
		}
		return null;
	}

	private static String calculateFingerprint(final Certificate cert) throws NoSuchAlgorithmException,
			CertificateEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] peer = cert.getEncoded();
		md.update(peer);
		byte[] bytSHA = md.digest();
		BigInteger intNumber = new BigInteger(1, bytSHA);
		return intNumber.toString(16).substring(7, 13).toUpperCase();
	}
}
