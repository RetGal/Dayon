package mpo.dayon.common.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class CustomTrustManager implements X509TrustManager {

	final X509TrustManager finalDefaultTm;
	final X509TrustManager finalOwnTm;

	public CustomTrustManager() {
		
		X509TrustManager defaultTm = null;
		X509TrustManager ownTm = null;
		
		TrustManagerFactory tmf;
		try {
			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		// using null here initialises the TMF with the default trust store.
		tmf.init((KeyStore) null);

		// get hold of the default trust manager
		for (TrustManager tm : tmf.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				defaultTm = (X509TrustManager) tm;
				break;
			}
		}
		
		final String keyStorePath = "/mpo/dayon/common/security/X509";
		final String keyStorePass = "spasspass";

		InputStream myKeys = getClass().getResourceAsStream(keyStorePath);

		// do the same with our own trust store this time
		KeyStore myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		myTrustStore.load(myKeys, keyStorePass.toCharArray());

		myKeys.close();

		tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(myTrustStore);

		// get hold of the default trust manager
		for (TrustManager tm : tmf.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				ownTm = (X509TrustManager) tm;
				break;
			}
		}
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		finalDefaultTm = defaultTm;
		finalOwnTm = ownTm;
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		// if you're planning to use client-cert auth, merge results from
		// "defaultTm" and "ownTm".
		return finalDefaultTm.getAcceptedIssuers();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			// check with own TM first
			finalOwnTm.checkServerTrusted(chain, authType);
		} catch (CertificateException e) {
			// this will throw another CertificateException if this fails
			// too.
			finalDefaultTm.checkServerTrusted(chain, authType);
		}
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		// if you're planning to use client-cert auth, do the same as
		// checking the server.
		finalDefaultTm.checkClientTrusted(chain, authType);
	}

}
