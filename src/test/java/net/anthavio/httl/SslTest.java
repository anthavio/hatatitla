package net.anthavio.httl;

import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509TrustManager;

import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.TransportBuilder.BaseTransBuilder;
import net.anthavio.httl.transport.HttpClient3Config;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpUrlConfig;

import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class SslTest {

	private static JokerServer server = new JokerServer();

	@BeforeClass
	public static void before() {
		server.start();
	}

	@AfterClass
	public static void after() {
		server.stop();
	}

	@Test
	public void httpClient4SelfSignedServer() {
		HttpClient4Config transport = HttlBuilder.httpClient4("https://localhost");
		testServerCert(transport);
	}

	@Test
	public void httpClient4MutualCertsAuth() {
		HttpClient4Config transport = HttlBuilder.httpClient4("https://localhost");
		testMutualCert(transport);
	}

	@Test
	public void httpClient3SelfSignedServer() {
		HttpClient3Config transport = HttlBuilder.httpClient3("https://localhost");
		testServerCert(transport);
	}

	@Test
	public void httpClient3MutualCertsAuth() {
		HttpClient3Config transport = HttlBuilder.httpClient3("https://localhost");
		testMutualCert(transport);
	}

	@Test
	public void httpUrlSelfSignedServer() {
		HttpUrlConfig transport = HttlBuilder.httpUrl("https://localhost");
		testServerCert(transport);
	}

	@Test
	public void httpUrlMutualCertsAuth() {
		HttpUrlConfig transport = HttlBuilder.httpUrl("https://localhost");
		testMutualCert(transport);
	}

	private void testMutualCert(BaseTransBuilder<?> transport) {
		HttlSender sender = transport.setUrl("https://localhost:" + server.getPortHttpsMutual()).sender().build();
		try {
			//When
			sender.GET("/").execute();
			Assertions.fail("HttlRequestException expected");
		} catch (HttlRequestException hrx) {
			//Then
			Assertions.assertThat(hrx.getCause()).isInstanceOf(SSLHandshakeException.class);
		}
		sender.close();

		//Given - keystore & truststore configured
		URL jks = getClass().getResource("/localhost.jks");
		SSLContext sslTrust = HttlBuilder.sslContext("TLS").addTrustStore(jks, "password")
				.addKeyStore(jks, "password", "password").build();
		sender = transport.setSslContext(sslTrust).sender().build();
		//When
		ExtractedResponse<String> respTrust = sender.GET("/").extract(String.class);
		//Then
		Assertions.assertThat(respTrust.getResponse().getHttpStatusCode()).isEqualTo(200);
		sender.close();
	}

	private void testServerCert(BaseTransBuilder<?> transport) {
		//Given - self signed server cert
		HttlSender sender = transport.setUrl("https://localhost:" + server.getPortHttps()).sender().build();
		try {
			//When
			sender.GET("/").execute();
			Assertions.fail("HttlRequestException expected");
		} catch (HttlRequestException hrx) {
			//Then
			Assertions.assertThat(hrx.getCause()).isInstanceOf(SSLHandshakeException.class);
		}
		sender.close();

		//Given - IdioticX509TrustManager 
		SSLContext sslIdiot = HttlBuilder.sslContext("TLS").addTrustManager(new IdioticX509TrustManager()).build();
		sender = transport.setUrl("https://localhost:" + server.getPortHttps()).setSslContext(sslIdiot).sender().build();
		//When
		ExtractedResponse<String> respIdiot = sender.GET("/").extract(String.class);
		//Then
		Assertions.assertThat(respIdiot.getResponse().getHttpStatusCode()).isEqualTo(200);
		sender.close();

		//Given - truststore configured
		URL jks = getClass().getResource("/localhost.jks");
		SSLContext sslTrust = HttlBuilder.sslContext("TLS").addTrustStore(jks, "password").build();
		sender = transport.setSslContext(sslTrust).sender().build();
		//When
		ExtractedResponse<String> respTrust = sender.GET("/").extract(String.class);
		//Then
		Assertions.assertThat(respTrust.getResponse().getHttpStatusCode()).isEqualTo(200);
		sender.close();
	}

	static class IdioticX509TrustManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
			//ignore
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
			//ignore
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

	}
}
