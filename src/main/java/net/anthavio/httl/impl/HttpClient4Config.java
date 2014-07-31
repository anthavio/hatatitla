package net.anthavio.httl.impl;

import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import net.anthavio.httl.Authentication;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.SenderBuilder;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

/**
 * 
 * @author martin.vanek
 * 
 */
public class HttpClient4Config extends SenderBuilder {

	private int poolReleaseTimeoutMillis = 65 * 1000;

	private int poolAcquireTimeoutMillis = 3 * 1000;

	private HttpContext authContext;

	private HttpClient4Transport transport;

	public HttpClient4Config(String url) {
		super(url);
	}

	public HttpClient4Config(URL url) {
		super(url);
	}

	@Override
	public HttlSender build() {
		transport = new HttpClient4Transport(this);
		return new HttlSender(this, transport);
	}

	public HttpClient4Transport getTransport() {
		return transport;
	}

	public HttpContext getAuthContext() {
		return this.authContext;
	}

	public HttpClient newHttpClient() {
		HttpParams httpParams = new BasicHttpParams();
		buildClientParams(httpParams, getUrl());
		buildConnectionParams(httpParams);
		buildProtocolParams(httpParams);
		ClientConnectionManager connectionManager = buildConnectionManager();

		DefaultHttpClient httpClient = new DefaultHttpClient(connectionManager, httpParams);

		if (getAuthentication() != null) {
			Authentication authentication = getAuthentication();
			UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(authentication.getUsername(),
					authentication.getPassword());
			CredentialsProvider provider = httpClient.getCredentialsProvider();
			AuthScope scope = new AuthScope(getUrl().getHost(), getUrl().getPort(), authentication.getRealm()/*, authentication
																																																								.getScheme().toString()*/);
			provider.setCredentials(scope, credentials);

			if (authentication.getPreemptive()) {
				AuthScheme scheme;
				if (authentication.getScheme() == Authentication.Scheme.BASIC) {
					BasicScheme basic = new BasicScheme();
					scheme = basic;
				} else {
					//http://stackoverflow.com/questions/2954434/apache-httpclient-digest-authentication
					DigestScheme digest = new DigestScheme();
					digest.overrideParamter("realm", authentication.getRealm());
					digest.overrideParamter("nonce", authentication.getNonce());
					scheme = digest;
				}
				HttpHost host = new HttpHost(getUrl().getHost(), getUrl().getPort(), getUrl().getProtocol());
				AuthCache authCache = new BasicAuthCache();
				authCache.put(host, scheme);

				this.authContext = new BasicHttpContext(); //Prepare execution context
				this.authContext.setAttribute(ClientContext.AUTH_CACHE, authCache);
				//authContext.setAttribute(ClientContext.CREDS_PROVIDER, provider);
			}

		}

		return httpClient;
	}

	protected ClientParamBean buildClientParams(HttpParams httpParams, URL url) {
		ClientParamBean clientBean = new ClientParamBean(httpParams);
		clientBean.setConnectionManagerTimeout(this.poolAcquireTimeoutMillis);//httpParams.setParameter(ClientPNames.CONN_MANAGER_TIMEOUT, 5000L);
		HttpHost httpHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
		clientBean.setDefaultHost(httpHost); //ClientPNames.DEFAULT_HOST
		clientBean.setHandleRedirects(getFollowRedirects());//ClientPNames.HANDLE_REDIRECTS
		return clientBean;
	}

	protected HttpConnectionParamBean buildConnectionParams(HttpParams httpParams) {
		HttpConnectionParamBean connectionBean = new HttpConnectionParamBean(httpParams);
		connectionBean.setConnectionTimeout(getConnectTimeoutMillis());//httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 1000L);
		connectionBean.setSoTimeout(getReadTimeoutMillis());//httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, 5000L);
		return connectionBean;
	}

	protected HttpProtocolParamBean buildProtocolParams(HttpParams httpParams) {
		HttpProtocolParamBean protocolBean = new HttpProtocolParamBean(httpParams);
		protocolBean.setVersion(HttpVersion.HTTP_1_1);
		protocolBean.setContentCharset(getEncoding());
		protocolBean.setHttpElementCharset(getEncoding());
		//paramsBean.setUseExpectContinue(true);

		return protocolBean;
	}

	protected PoolingClientConnectionManager buildConnectionManager() {
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

		//set strict hostname verifier
		SSLContext sslcontext;
		try {
			sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(null, null, null);
		} catch (NoSuchAlgorithmException nsax) {
			throw new IllegalArgumentException(nsax);
		} catch (KeyManagementException kmx) {
			throw new IllegalArgumentException(kmx);
		}
		SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslcontext, SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
		schemeRegistry.register(new Scheme("https", 443, sslSocketFactory));

		//we access only one host
		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry,
				this.poolReleaseTimeoutMillis, TimeUnit.MILLISECONDS);
		connectionManager.setMaxTotal(getPoolMaximumSize());
		connectionManager.setDefaultMaxPerRoute(getPoolMaximumSize());
		return connectionManager;
	}

	public int getPoolReleaseTimeoutMillis() {
		return this.poolReleaseTimeoutMillis;
	}

	public void setPoolReleaseTimeoutMillis(int millis) {
		this.poolReleaseTimeoutMillis = millis;
	}

	public int getPoolAcquireTimeoutMillis() {
		return this.poolAcquireTimeoutMillis;
	}

	public void setPoolAcquireTimeoutMillis(int millis) {
		this.poolAcquireTimeoutMillis = millis;
	}

}

class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

	public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
		AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

		// If no auth scheme avaialble yet, try to initialize it preemptively
		if (authState.getAuthScheme() == null) {
			AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
			CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
			HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
			if (authScheme != null) {
				Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
				if (creds == null) {
					throw new HttpException("No credentials for preemptive authentication");
				}
				authState.update(authScheme, creds);
			}
		}

	}

}
