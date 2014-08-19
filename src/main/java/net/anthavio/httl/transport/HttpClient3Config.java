package net.anthavio.httl.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

import net.anthavio.httl.Authentication;
import net.anthavio.httl.Authentication.Scheme;
import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ControllerThreadSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * http://hc.apache.org/httpclient-legacy/preference-api.html
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Config extends BaseTransBuilder<HttpClient3Config> {

	private int poolReleaseTimeoutMillis = 35 * 1000;

	private int poolAcquireTimeoutMillis = 3 * 1000;

	public HttpClient3Config(String url) {
		super(url);
	}

	public HttpClient3Config(URL url) {
		super(url);
	}

	@Override
	public HttpClient3Transport build() {
		return new HttpClient3Transport(this);
	}

	@Override
	public HttpClient3Config getSelf() {
		return this;
	}

	public int getPoolReleaseTimeoutMillis() {
		return poolReleaseTimeoutMillis;
	}

	public HttpClient3Config setPoolReleaseTimeoutMillis(int poolReleaseTimeout) {
		this.poolReleaseTimeoutMillis = poolReleaseTimeout;
		return getSelf();
	}

	public int getPoolAcquireTimeoutMillis() {
		return poolAcquireTimeoutMillis;
	}

	public HttpClient3Config setPoolAcquireTimeoutMillis(int poolAcquireTimeout) {
		this.poolAcquireTimeoutMillis = poolAcquireTimeout;
		return getSelf();
	}

	/**
	 * http://hc.apache.org/httpclient-3.x/preference-api.html
	 */
	public HttpClient newHttpClient() {
		HttpClientParams clientParams = new HttpClientParams();
		clientParams.setVersion(HttpVersion.HTTP_1_1);
		clientParams.setSoTimeout(getReadTimeoutMillis());//http.socket.timeout
		clientParams.setContentCharset(getCharset());//http.protocol.content-charset
		clientParams.setHttpElementCharset(getCharset());
		clientParams.setConnectionManagerTimeout(poolAcquireTimeoutMillis); //http.connection-manager.timeout

		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		HttpConnectionManagerParams managerParams = new HttpConnectionManagerParams();
		HostConfiguration hostConfig = new HostConfiguration();

		if (getSslContext() != null) {
			Protocol https = new Protocol("https", new Hc3SecureSocketFactory(getSslContext().getSocketFactory()), 443);
			hostConfig.setHost(getUrl().getHost(), getUrl().getPort(), https);
		} else {
			hostConfig.setHost(getUrl().getHost(), getUrl().getPort(), getUrl().getProtocol());
		}
		managerParams.setMaxConnectionsPerHost(hostConfig, getPoolMaximumSize());
		managerParams.setMaxTotalConnections(getPoolMaximumSize());
		managerParams.setConnectionTimeout(getConnectTimeoutMillis());//http.connection.timeout
		connectionManager.setParams(managerParams);

		HttpClient httpClient = new HttpClient(clientParams, connectionManager);
		httpClient.setHostConfiguration(hostConfig);

		//http://hc.apache.org/httpclient-3.x/authentication.html
		if (getAuthentication() != null) {
			Authentication authentication = getAuthentication();

			UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(authentication.getUsername(),
					authentication.getPassword());
			AuthScope scope = new AuthScope(getUrl().getHost(), getUrl().getPort(), authentication.getRealm()/*, authentication
																																																								.getScheme().toString()*/);
			httpClient.getState().setCredentials(scope, credentials);

			if (authentication.getPreemptive() && authentication.getScheme() == Scheme.BASIC) {
				//Only BASIC can be preemtive
				clientParams.setAuthenticationPreemptive(authentication.getPreemptive());
			}
		}

		return httpClient;
	}

	/**
	 * http://hc.apache.org/httpclient-3.x/sslguide.html
	 * 
	 * @author martin.vanek
	 *
	 */
	static class Hc3SecureSocketFactory implements SecureProtocolSocketFactory {

		private final SSLSocketFactory socketFactory;

		public Hc3SecureSocketFactory(SSLSocketFactory socketFactory) {
			this.socketFactory = socketFactory;
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException,
				UnknownHostException {
			return socketFactory.createSocket(host, port, localAddress, localPort);
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localAddress, int localPort,
				HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
			if (params == null) {
				throw new IllegalArgumentException("Parameters may not be null");
			}
			int timeout = params.getConnectionTimeout();
			if (timeout == 0) {
				return createSocket(host, port, localAddress, localPort);
			} else {
				//from org.apache.commons.httpclient.protocol.SSLProtocolSocketFactory
				return ControllerThreadSocketFactory.createSocket(this, host, port, localAddress, localPort, timeout);
			}
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return socketFactory.createSocket(host, port);
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException,
				UnknownHostException {
			return socketFactory.createSocket(socket, host, port, autoClose);
		}

	}

}
