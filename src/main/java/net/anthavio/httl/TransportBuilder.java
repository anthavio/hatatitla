package net.anthavio.httl;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.net.ssl.SSLContext;

import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.SSLContextBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public interface TransportBuilder<S extends TransportBuilder<?>> {

	/**
	 * @return final HttlTransport
	 */
	public HttlTransport build();

	/**
	 * @return generic self
	 */
	public S getSelf();

	/**
	 * 
	 * @author martin.vanek
	 *
	 * @param <S>
	 */
	public static abstract class BaseTransBuilder<S extends BaseTransBuilder<?>> implements TransportBuilder<S> {

		private URL url;

		private SSLContext sslContext;

		private Authentication authentication;

		protected int poolMaximumSize = 10; //maximal number of pooled connections

		protected int connectTimeoutMillis = 5 * 1000; //in millis

		protected int readTimeoutMillis = 10 * 1000; //in millis

		protected boolean followRedirects = false;

		private String charset = "utf-8";

		private Charset javaCharset = Charset.forName(this.charset);

		public BaseTransBuilder(URL url) {
			this.url = trimUrl(url);
		}

		public BaseTransBuilder(String urlString) {
			setUrl(urlString);
		}

		public S setUrl(String urlString) {
			if (Cutils.isBlank(urlString)) {
				throw new IllegalArgumentException("URL is blank");
			}
			if (urlString.startsWith("http") == false) {
				urlString = "http://" + urlString;
			}
			//try to parse
			URL url;
			try {
				url = new URL(urlString);
			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException("URL is invalid " + urlString, mux);
			}
			if (Cutils.isBlank(url.getHost())) {
				throw new IllegalArgumentException("URL has no host " + urlString);
			}
			this.url = trimUrl(url);
			return getSelf();
		}

		/**
		 * Finalize Transport creation and continue with sender builder...
		 */
		public SenderConfigurer sender() {
			HttlTransport transport = build();
			return new SenderConfigurer(transport);
		}

		/**
		 * Cut-off query from url
		 */
		private URL trimUrl(URL url) {
			try {
				url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath());
			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException(mux);
			}
			return url;
		}

		public URL getUrl() {
			return this.url;
		}

		public Authentication getAuthentication() {
			return this.authentication;
		}

		/**
		 * BASIC, DIGEST...
		 */
		public S setAuthentication(Authentication authentication) {
			this.authentication = authentication;
			return getSelf();
		}

		/**
		 * @return Timeout for creating connection in millis (CONNECTION_TIMEOUT)
		 */
		public int getConnectTimeoutMillis() {
			return this.connectTimeoutMillis;
		}

		/**
		 * Timeout for creating connection in millis (CONNECTION_TIMEOUT)
		 */
		public S setConnectTimeoutMillis(int millis) {
			this.connectTimeoutMillis = millis;
			return getSelf();
		}

		/**
		 * @return Timeout for reading response in millis (SO_TIMEOUT)
		 */
		public int getReadTimeoutMillis() {
			return this.readTimeoutMillis;
		}

		/**
		 * Timeout for reading response in millis (SO_TIMEOUT)
		 */
		public S setReadTimeoutMillis(int millis) {
			this.readTimeoutMillis = millis;
			return getSelf();
		}

		/**
		 * @return Maximal number of pooled connections
		 */
		public int getPoolMaximumSize() {
			return poolMaximumSize;
		}

		/**
		 * Maximal number of pooled connections
		 */
		public S setPoolMaximumSize(int poolMaximum) {
			this.poolMaximumSize = poolMaximum;
			return getSelf();
		}

		public boolean getFollowRedirects() {
			return followRedirects;
		}

		public S setFollowRedirects(boolean followRedirects) {
			this.followRedirects = followRedirects;
			return getSelf();
		}

		public String getCharset() {
			return this.charset;
		}

		public S setCharset(String encoding) {
			this.charset = encoding;
			this.javaCharset = Charset.forName(encoding);
			//Update Content-Type header is already exists
			//XXX do this on build()
			/*
			String contentType = this.defaultHeaders.getFirst(HttlConstants.Content_Type);
			if (contentType != null) {
				this.defaultHeaders.set(HttlConstants.Content_Type, contentType.substring(0, contentType.indexOf("; charset="))
						+ "; charset=" + encoding);
			}
			*/
			return getSelf();
		}

		public Charset getJavaCharset() {
			return this.javaCharset;
		}

		/**
		 * Custom SSLContext allows overriding TrustManager etc
		 * 
		 * Probably use {@link SSLContextBuilder} to create SSLContext
		 */
		public S setSslContext(SSLContext sslContext) {
			this.sslContext = sslContext;
			return getSelf();
		}

		public SSLContext getSslContext() {
			return sslContext;
		}
	}

}
