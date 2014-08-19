package net.anthavio.httl.util;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Example how to create security flaw by ignoring certificate hostname:
 * 
 * SSLContext sslContext = SSLContextBuilder.TLS().addTrustManager(new SubjectDNPatternTrustManager(".*"))
				.build();
 * 
 * @author martin.vanek
 *
 */
public class SSLContextBuilder {

	private static final Logger log = LoggerFactory.getLogger(SSLContextBuilder.class);

	private String protocol; //SSL, SSLv3, TLS, TLSv1.2

	private List<TrustManager> trustManagers;

	private List<KeyManager> keyManagers;

	private java.security.Provider provider;

	private String providerName;

	private SecureRandom secureRandom;

	public static SSLContextBuilder TLS() {
		return new SSLContextBuilder("TLS");
	}

	public static SSLContextBuilder SSL() {
		return new SSLContextBuilder("SSL");
	}

	public static SSLContextBuilder Protocol(String protocol) {
		return new SSLContextBuilder(protocol);
	}

	public SSLContextBuilder(String protocol) {
		if (Cutils.isBlank(protocol)) {
			throw new IllegalArgumentException("Invalid protocol: " + protocol);
		}
		this.protocol = protocol;
	}

	public java.security.Provider getProvider() {
		return provider;
	}

	public SSLContextBuilder setProvider(java.security.Provider provider) {
		this.provider = provider;
		return this;
	}

	public String getProviderName() {
		return providerName;
	}

	public SSLContextBuilder setProviderName(String providerName) {
		this.providerName = providerName;
		return this;
	}

	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	public SSLContextBuilder setSecureRandom(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
		return this;
	}

	public SSLContextBuilder addKeyManager(URL storeUrl, String storePassword, String keyPassword) {
		if (storeUrl == null) {
			throw new IllegalArgumentException("Null keystore url");
		}
		KeyStore keyStore = loadKeyStore(storeUrl, storePassword);
		return addKeyManager(keyStore, keyPassword);
	}

	public SSLContextBuilder addTrustManager(URL storeUrl, String storePassword) {
		if (storeUrl == null) {
			throw new IllegalArgumentException("Null keystore url");
		}
		KeyStore keyStore = loadKeyStore(storeUrl, storePassword);
		return addTrustManager(keyStore);
	}

	public SSLContextBuilder addTrustManager(KeyStore keystore) {
		try {
			TrustManager[] managers = createTrustManagers(keystore);
			for (TrustManager manager : managers) {
				addTrustManager(manager);
			}
		} catch (Exception x) {
			throw new IllegalArgumentException("Failed to create TrustManager from KeyStore " + keystore);
		}
		return this;
	}

	public SSLContextBuilder addKeyManager(KeyStore keystore, String password) {
		try {
			KeyManager[] managers = createKeyManagers(keystore, password);
			for (KeyManager manager : managers) {
				addKeyManager(manager);
			}
		} catch (Exception x) {
			throw new IllegalArgumentException("Failed to create KeyManagers from KeyStore " + keystore);
		}
		return this;
	}

	public SSLContextBuilder addTrustManager(TrustManager trustManager) {
		if (trustManager == null) {
			throw new IllegalArgumentException("Null TrustManager");
		}
		if (trustManagers == null) {
			trustManagers = new ArrayList<TrustManager>();
		}
		trustManagers.add(trustManager);
		return this;
	}

	public SSLContextBuilder addKeyManager(KeyManager keyManager) {
		if (keyManager == null) {
			throw new IllegalArgumentException("Null KeyManager");
		}
		if (keyManagers == null) {
			keyManagers = new ArrayList<KeyManager>();
		}
		keyManagers.add(keyManager);
		return this;
	}

	public SSLContext build() {
		SSLContext sslContext;
		try {
			if (provider != null) {
				sslContext = SSLContext.getInstance(protocol, provider);
			} else if (providerName != null) {
				sslContext = SSLContext.getInstance(protocol, providerName);
			} else {
				sslContext = SSLContext.getInstance(protocol);
			}
		} catch (NoSuchAlgorithmException nsax) {
			throw new IllegalArgumentException("Invalid protocol " + protocol, nsax);
		} catch (NoSuchProviderException nspx) {
			throw new IllegalArgumentException("Invalid provider " + providerName, nspx);
		}

		KeyManager[] akm = keyManagers != null ? keyManagers.toArray(new KeyManager[keyManagers.size()]) : null;
		TrustManager[] atm = trustManagers != null ? trustManagers.toArray(new TrustManager[trustManagers.size()]) : null;
		SecureRandom random = this.secureRandom != null ? this.secureRandom : new SecureRandom();
		try {
			sslContext.init(akm, atm, random);
		} catch (KeyManagementException kmx) {
			throw new IllegalArgumentException("SSLContext initialization failed " + sslContext, kmx);
		}

		return sslContext;
	}

	public static KeyStore loadKeyStore(URL location, String password) {
		try {
			KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream storeStream = location.openStream();
			try {
				store.load(storeStream, password != null ? password.toCharArray() : null);
			} finally {
				storeStream.close();
			}
			return store;
		} catch (Exception x) {
			throw new IllegalArgumentException("Cannot initialize keyStore " + location, x);
		}
	}

	public static KeyManager[] createKeyManagers(final KeyStore keystore, final String password)
			throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
		if (keystore == null) {
			throw new IllegalArgumentException("Keystore may not be null");
		}
		log.debug("Initializing KeyManagerFactory with algorithm " + KeyManagerFactory.getDefaultAlgorithm());
		KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmfactory.init(keystore, password != null ? password.toCharArray() : null);
		return kmfactory.getKeyManagers();
	}

	public static TrustManager[] createTrustManagers(final KeyStore keystore) throws KeyStoreException,
			NoSuchAlgorithmException {
		if (keystore == null) {
			throw new IllegalArgumentException("Keystore may not be null");
		}
		log.debug("Initializing TrustManagerFactory with algorithm " + TrustManagerFactory.getDefaultAlgorithm());
		TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmfactory.init(keystore);
		TrustManager[] trustmanagers = tmfactory.getTrustManagers();
		for (int i = 0; i < trustmanagers.length; i++) {
			if (trustmanagers[i] instanceof X509TrustManager) {
				log.debug("Adding X509TrustManager " + trustmanagers[i]);
				trustmanagers[i] = new X509TrustManagerWrapper((X509TrustManager) trustmanagers[i]);
			}
		}
		return trustmanagers;
	}

	private static void logCertInfo(X509Certificate cert) {
		log.trace("  Subject DN: " + cert.getSubjectDN());
		log.trace("  Signature Algorithm: " + cert.getSigAlgName());
		log.trace("  Valid from: " + cert.getNotBefore());
		log.trace("  Valid until: " + cert.getNotAfter());
		log.trace("  Issuer: " + cert.getIssuerDN());
	}

	public static void logKeyStoreContent(KeyStore keyStore, boolean keys) throws KeyStoreException {
		if (keys) {
			Enumeration<String> aliases = keyStore.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				Certificate[] certs = keyStore.getCertificateChain(alias);
				if (certs != null) {
					if (log.isTraceEnabled()) {
						log.debug("Certificate chain '" + alias + "':");
						for (int c = 0; c < certs.length; c++) {
							if (certs[c] instanceof X509Certificate) {
								X509Certificate cert = (X509Certificate) certs[c];
								log.trace(" Certificate " + (c + 1) + ":");
								logCertInfo(cert);
							}
						}
					} else if (log.isDebugEnabled()) {
						log.debug("Certificate chain '" + alias + "' Subject DN: " + ((X509Certificate) certs[0]).getSubjectDN());
					}
				}
			}
		} else {
			Enumeration<String> aliases = keyStore.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if (log.isTraceEnabled()) {
					log.debug("Trusted certificate '" + alias + "':");
					Certificate trustedcert = keyStore.getCertificate(alias);
					if (trustedcert instanceof X509Certificate) {
						X509Certificate cert = (X509Certificate) trustedcert;
						logCertInfo(cert);
					}
				} else if (log.isDebugEnabled()) {
					log.debug("Trusted certificate '" + alias + "' Subject DN: "
							+ ((X509Certificate) keyStore.getCertificate(alias)).getSubjectDN());
				}
			}
		}
	}

	/**
	 * Just logs certificate checks
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class X509TrustManagerWrapper implements X509TrustManager {

		private X509TrustManager delegate = null;

		public X509TrustManagerWrapper(final X509TrustManager delegate) {
			if (delegate == null) {
				throw new IllegalArgumentException("Trust manager may not be null");
			}
			this.delegate = delegate;
		}

		/**
		 * @see javax.net.ssl.X509TrustManager#checkClientTrusted(X509Certificate[],String authType)
		 */
		public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
			if (log.isDebugEnabled() && certificates != null) {
				for (int c = 0; c < certificates.length; c++) {
					X509Certificate cert = certificates[c];
					if (log.isTraceEnabled()) {
						log.debug("Check client cert" + (c + 1) + ":");
						logCertInfo(cert); //trace level -> log details
					} else if (log.isDebugEnabled()) {
						log.debug("Check client cert" + (c + 1) + " Subject DN: " + cert.getSubjectDN());
					}
				}
			}
			try {
				delegate.checkClientTrusted(certificates, authType);
			} catch (CertificateException cx) {
				log.debug("Check client cert failed: " + cx);
				throw cx;
			}
		}

		/**
		 * @see javax.net.ssl.X509TrustManager#checkServerTrusted(X509Certificate[],String authType)
		 */
		public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
			if (log.isInfoEnabled() && certificates != null) {
				for (int c = 0; c < certificates.length; c++) {
					X509Certificate cert = certificates[c];
					if (log.isTraceEnabled()) {
						log.debug("Check server cert" + (c + 1) + ":");
						logCertInfo(cert); //trace level -> log details
					} else if (log.isDebugEnabled()) {
						log.debug("Check server cert" + (c + 1) + " Subject DN: " + cert.getSubjectDN());
					}
				}
			}
			try {
				delegate.checkServerTrusted(certificates, authType);
			} catch (CertificateException cx) {
				log.debug("Check server cert failed: " + cx);
				throw cx;
			}
		}

		/**
		 * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
		 */
		public X509Certificate[] getAcceptedIssuers() {
			return delegate.getAcceptedIssuers();
		}
	}
}
