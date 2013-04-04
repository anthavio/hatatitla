package com.anthavio.hatatitla;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

import com.anthavio.hatatitla.Authentication.Scheme;

/**
 * http://hc.apache.org/httpclient-legacy/preference-api.html
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Config extends HttpSenderConfig {

	private int poolReleaseTimeoutMillis = 65 * 1000;

	private int poolAcquireTimeoutMillis = 3 * 1000;

	private int poolMaximumSize = 10;

	public HttpClient3Config(String url) {
		super(url);
	}

	public HttpClient3Sender buildSender() {
		return new HttpClient3Sender(this);
	}

	/**
	 * http://hc.apache.org/httpclient-3.x/preference-api.html
	 */
	public HttpClient buildHttpClient() {
		HttpClientParams clientParams = new HttpClientParams();
		clientParams.setVersion(HttpVersion.HTTP_1_1);
		clientParams.setSoTimeout(getReadTimeoutMillis());//http.socket.timeout
		clientParams.setContentCharset(getEncoding());//http.protocol.content-charset
		clientParams.setHttpElementCharset(getEncoding());
		clientParams.setConnectionManagerTimeout(poolAcquireTimeoutMillis); //http.connection-manager.timeout

		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		HttpConnectionManagerParams managerParams = new HttpConnectionManagerParams();
		HostConfiguration hostConfig = new HostConfiguration();
		hostConfig.setHost(getHostUrl().getHost(), getHostUrl().getPort(), getHostUrl().getProtocol());
		managerParams.setMaxConnectionsPerHost(hostConfig, poolMaximumSize);
		managerParams.setMaxTotalConnections(poolMaximumSize);
		managerParams.setConnectionTimeout(getConnectTimeoutMillis());//http.connection.timeout
		connectionManager.setParams(managerParams);

		HttpClient httpClient = new HttpClient(clientParams, connectionManager);
		httpClient.setHostConfiguration(hostConfig);

		//http://hc.apache.org/httpclient-3.x/authentication.html
		if (getAuthentication() != null) {
			Authentication authentication = getAuthentication();

			UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(authentication.getUsername(),
					authentication.getPassword());
			AuthScope scope = new AuthScope(getHostUrl().getHost(), getHostUrl().getPort(), authentication.getRealm()/*, authentication
																																																								.getScheme().toString()*/);
			httpClient.getState().setCredentials(scope, credentials);

			if (authentication.getPreemptive() && authentication.getScheme() == Scheme.BASIC) {
				//Only BASIC can be preemtive
				clientParams.setAuthenticationPreemptive(authentication.getPreemptive());
			}
		}

		return httpClient;
	}

	public int getPoolReleaseTimeoutMillis() {
		return poolReleaseTimeoutMillis;
	}

	public void setPoolReleaseTimeoutMillis(int poolReleaseTimeout) {
		this.poolReleaseTimeoutMillis = poolReleaseTimeout;
	}

	public int getPoolAcquireTimeoutMillis() {
		return poolAcquireTimeoutMillis;
	}

	public void setPoolAcquireTimeoutMillis(int poolAcquireTimeout) {
		this.poolAcquireTimeoutMillis = poolAcquireTimeout;
	}

	public int getPoolMaximumSize() {
		return poolMaximumSize;
	}

	public void setPoolMaximumSize(int poolMaximum) {
		this.poolMaximumSize = poolMaximum;
	}

}
