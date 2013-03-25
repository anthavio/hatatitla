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

	private int poolReleaseTimeout = 65 * 1000;

	private int poolAcquireTimeout = 3 * 1000;

	private int poolMaximum = 10;

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
		clientParams.setSoTimeout(getReadTimeout());//http.socket.timeout
		clientParams.setContentCharset(getEncoding());//http.protocol.content-charset
		clientParams.setHttpElementCharset(getEncoding());
		clientParams.setConnectionManagerTimeout(poolAcquireTimeout); //http.connection-manager.timeout

		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		HttpConnectionManagerParams managerParams = new HttpConnectionManagerParams();
		HostConfiguration hostConfig = new HostConfiguration();
		hostConfig.setHost(getHostUrl().getHost(), getHostUrl().getPort(), getHostUrl().getProtocol());
		managerParams.setMaxConnectionsPerHost(hostConfig, poolMaximum);
		managerParams.setMaxTotalConnections(poolMaximum);
		managerParams.setConnectionTimeout(getConnectTimeout());//http.connection.timeout
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

	public int getPoolReleaseTimeout() {
		return poolReleaseTimeout;
	}

	public void setPoolReleaseTimeout(int poolReleaseTimeout) {
		this.poolReleaseTimeout = poolReleaseTimeout;
	}

	public int getPoolAcquireTimeout() {
		return poolAcquireTimeout;
	}

	public void setPoolAcquireTimeout(int poolAcquireTimeout) {
		this.poolAcquireTimeout = poolAcquireTimeout;
	}

	public int getPoolMaximum() {
		return poolMaximum;
	}

	public void setPoolMaximum(int poolMaximum) {
		this.poolMaximum = poolMaximum;
	}

}
