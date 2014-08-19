package net.anthavio.httl.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Rather use Keystore based TrustManager
 * 
 * Not very secure but usable in test environments with self-signed certificates 
 * where keystores setup and configuration could be overkill
 * 
 * @author martin.vanek
 *
 */
public class SubjectDNPatternTrustManager implements javax.net.ssl.X509TrustManager {

	private final Pattern hostnamePattern;

	public SubjectDNPatternTrustManager(String hostnameRegex) {
		hostnamePattern = Pattern.compile(hostnameRegex);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new CertificateException("Can't validate client certs");
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		X509Certificate cert = chain[0];
		String hostName = cert.getSubjectDN().getName();
		if (!hostnamePattern.matcher(hostName).find()) {
			throw new CertificateException("Certificate SubjectDN " + hostName + " does not match " + hostnamePattern);
		}
		Date date = new Date();
		if (date.after(cert.getNotAfter())) {
			throw new CertificateException("Certificate is expired " + cert.getNotAfter());
		}

		if (date.before(cert.getNotBefore())) {
			throw new CertificateException("Certificate is not yet valid " + cert.getNotBefore());
		}
		//TODO maybe check key usage
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return null;
	}

}
