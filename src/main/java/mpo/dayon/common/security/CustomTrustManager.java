package mpo.dayon.common.security;

import mpo.dayon.common.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class CustomTrustManager implements X509TrustManager {

	public static final String KEY_STORE_PATH = "/trust/X509";
	public static final String KEY_STORE_PASS = "spasspass";

	private final X509TrustManager defaultTm;
	private final X509TrustManager ownTm;
	private static String fingerprint;

	@java.lang.SuppressWarnings("squid:S6437") // pro forma password, without security relevance
	public CustomTrustManager() {

		TrustManagerFactory tmf;
		try {
			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			// using null here initializes the TMF with the default trust store.
			tmf.init((KeyStore) null);
			// get hold of the default trust manager
			defaultTm = getDefaultX509TrustManager(tmf);

			// do the same with our own trust store this time
			KeyStore myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream myKeys = getClass().getResourceAsStream(KEY_STORE_PATH);
			myTrustStore.load(myKeys, KEY_STORE_PASS.toCharArray());
			if (myKeys != null) {
				myKeys.close();
			}

			if (fingerprint == null) {
				fingerprint = calculateFingerprint(myTrustStore.getCertificate("mykey"));
			}

			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(myTrustStore);
			// get hold of the default trust manager
			ownTm = getDefaultX509TrustManager(tmf);
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
			Log.error(e.getMessage());
			throw new UnsupportedOperationException(e);
		}
	}

	private X509TrustManager getDefaultX509TrustManager(TrustManagerFactory tmf) throws NoSuchAlgorithmException {
		for (TrustManager tm : tmf.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				return (X509TrustManager) tm;
			}
		}
		throw new NoSuchAlgorithmException();
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return Stream.concat(Arrays.stream(ownTm.getAcceptedIssuers()), Arrays.stream(defaultTm.getAcceptedIssuers()))
				.toArray(size -> (X509Certificate[]) Array.newInstance(ownTm.getAcceptedIssuers().getClass().getComponentType(), size));
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			// check with own TM first
			ownTm.checkServerTrusted(chain, authType);
		} catch (CertificateException e) {
			// this will throw another CertificateException if this fails too.
			defaultTm.checkServerTrusted(chain, authType);
		}
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			// check with own TM first
			ownTm.checkClientTrusted(chain, authType);
		} catch (CertificateException e) {
			// this will throw another CertificateException if this fails too.
			defaultTm.checkClientTrusted(chain, authType);
		}
	}

	public static boolean isValidFingerprint(String fingerprint) {
		return fingerprint != null && fingerprint.equals(CustomTrustManager.fingerprint);
	}

	public static String calculateFingerprint(final Certificate cert) throws NoSuchAlgorithmException,
			CertificateEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] peer = cert.getEncoded();
		md.update(peer);
		byte[] bytSHA = md.digest();
		BigInteger intNumber = new BigInteger(1, bytSHA);
		return intNumber.toString(16);
	}
}
