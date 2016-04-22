package net.anthavio.httl;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.net.ssl.SSLContext;

import net.anthavio.httl.transport.HttlTarget;
import net.anthavio.httl.transport.HttlTarget.SingleHttlTarget;
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
	public static abstract class BaseTransportBuilder<S extends BaseTransportBuilder<?>> implements TransportBuilder<S> {

		private HttlTarget target;

		private SSLContext sslContext;

		private Authentication authentication;

		protected int poolMaximumSize = 10; //maximal number of pooled connections

		protected int connectTimeoutMillis = 5 * 1000; //in millis

		protected int readTimeoutMillis = 10 * 1000; //in millis

		protected boolean followRedirects = false;

		private String charset = "utf-8";

		private Charset javaCharset = Charset.forName(this.charset);

		/**
		 * Copy constructor
		 */
		public BaseTransportBuilder(BaseTransportBuilder<?> from) {
			//this.url = from.url;
			this.target = from.target;
			this.sslContext = from.sslContext;
			this.authentication = from.authentication;
			this.poolMaximumSize = from.poolMaximumSize;
			this.connectTimeoutMillis = from.connectTimeoutMillis;
			this.readTimeoutMillis = from.readTimeoutMillis;
			this.followRedirects = from.followRedirects;
			this.charset = from.charset;
			this.javaCharset = from.javaCharset;
		}

		public BaseTransportBuilder(URL url) {
			this(new SingleHttlTarget(trimUrl(url)));
		}

		public BaseTransportBuilder(String urlString) {
			setUrl(urlString);
		}

		public BaseTransportBuilder(HttlTarget target) {
			this.target = target;
			setUrl(target.getUrl().toString());
		}

		public HttlTarget getTarget() {
			return target;
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
			this.target = new SingleHttlTarget(trimUrl(url));
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
		protected static URL trimUrl(URL url) {
			try {
				url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath());
			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException(mux);
			}
			return url;
		}

		/*
				public URL getUrl() {
					return this.target.getUrl();
				}
		*/
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
