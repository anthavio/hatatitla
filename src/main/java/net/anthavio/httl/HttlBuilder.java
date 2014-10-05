package net.anthavio.httl;

import net.anthavio.httl.api.HttlApiBuilder;
import net.anthavio.httl.transport.HttpClient3Config;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpUrlConfig;
import net.anthavio.httl.transport.HttpUrlTransport;
import net.anthavio.httl.util.MockTransport;
import net.anthavio.httl.util.OptionalLibs;
import net.anthavio.httl.util.SSLContextBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlBuilder {

	private HttlBuilder() {
		//no instances
	}

	/**
	 * Commence new Transport creation
	 */
	public static TransportChooser transport(String url) {
		return new TransportChooser(url);
	}

	/**
	 * Commence new HttpClient4 Transport creation
	 */
	public static HttpClient4Config httpClient4(String url) {
		if (OptionalLibs.isHttpClient4) {
			return new HttpClient4Config(url);
		} else {
			throw new IllegalStateException("HttClient 4 classes not found in classpath");
		}
	}

	/**
	 * Commence new HttpClient3 Transport creation
	 */
	public static HttpClient3Config httpClient3(String url) {
		if (OptionalLibs.isHttpClient3) {
			return new HttpClient3Config(url);
		} else {
			throw new IllegalStateException("HttClient 3.1 classes not found in classpath");
		}
	}

	/**
	 * Commence new HttpURLConnection Transport creation
	 */
	public static HttpUrlConfig httpUrl(String url) {
		return new HttpUrlConfig(url);
	}

	/**
	 * Commence new Mock Transport creation
	 */
	public static MockTransport mock(String url) {
		return new MockTransport(url);
	}

	/**
	 * Commence new Sender creation...
	 */
	public static TransportChooser sender(String url) {
		return new TransportChooser(url);
	}

	/**
	 * Commence new Sender creation...
	 */
	public static SenderConfigurer sender(HttlTransport transport) {
		return new SenderConfigurer(transport);
	}

	/**
	 * Commence new Rest API creation...
	 */
	public static HttlApiBuilder api(HttlSender sender) {
		return HttlApiBuilder.with(sender);
	}

	/**
	 * Commence new SSLContext creation...
	 * @param protocol - TLS, TLSv1.2, SSL, SSLv3
	 */
	public static SSLContextBuilder sslContext(String protocol) {
		return new SSLContextBuilder(protocol);
	}

	public static class TransportChooser {

		private String url;

		private TransportChooser(String url) {
			this.url = url;
		}

		/**
		 * Shortcut for SenderConfigurer
		 * 
		 * @return Java HttpURLConnection backed Sender Configurer
		 */
		public SenderConfigurer config() {
			HttpUrlConfig config = new HttpUrlConfig(url);
			HttpUrlTransport transport = config.build();
			return new SenderConfigurer(transport);
		}

		/**
		 * Build final HttlSender
		 * 
		 * @return Java HttpURLConnection based Sender
		 */
		public HttlSender build() {
			return new HttlSender(config());
		}

		/**
		 * @return Java HttpURLConnection based Transport Configurer
		 */
		public HttpUrlConfig httpUrl() {
			return new HttpUrlConfig(url);
		}

		/**
		 * @return Apache HttpClient 3 based Transport Configurer
		 */
		public HttpClient3Config httpClient3() {
			if (OptionalLibs.isHttpClient3) {
				return new HttpClient3Config(url);
			} else {
				throw new IllegalStateException("HttClient 3.1 classes not found in classpath");
			}
		}

		/**
		 * @return Apache HttpClient 4 based Transport Configurer
		 */
		public HttpClient4Config httpClient4() {
			if (OptionalLibs.isHttpClient4) {
				return new HttpClient4Config(url);
			} else {
				throw new IllegalStateException("HttClient 4 classes not found in classpath");
			}
		}

		/**
		 * @return Mocking Transport Configurer
		 */
		public MockTransport mock() {
			return new MockTransport(url);
		}
	}

}
