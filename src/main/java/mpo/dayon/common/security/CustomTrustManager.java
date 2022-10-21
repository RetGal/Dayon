package mpo.dayon.common.security;

import mpo.dayon.common.log.Log;

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
	
	public static final String KEY_STORE_PATH = "/trust/X509";
	public static final String KEY_STORE_PASS = "spasspass";

	private X509TrustManager defaultTm;
	private X509TrustManager ownTm;

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
			myKeys.close();
	
			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(myTrustStore);
			// get hold of the default trust manager
			ownTm = getDefaultX509TrustManager(tmf);
		} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
			Log.error(e.getMessage());
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
		// if you're planning to use client-cert auth, merge results from
		// "defaultTm" and "ownTm".
		return defaultTm.getAcceptedIssuers();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			// check with own TM first
			ownTm.checkServerTrusted(chain, authType);
		} catch (CertificateException e) {
			// this will throw another CertificateException if this fails
			// too.
			defaultTm.checkServerTrusted(chain, authType);
		}
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		// if you're planning to use client-cert auth, do the same as
		// checking the server.
		defaultTm.checkClientTrusted(chain, authType);
	}

}
