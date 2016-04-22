package net.anthavio.httl.transport;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import net.anthavio.httl.Authentication;
import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;

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
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

/**
 * 
 * @author martin.vanek
 * 
 */
public class HttpClient4Config extends BaseTransportBuilder<HttpClient4Config> {

	private int poolReleaseTimeoutMillis = 15 * 1000; //apache 2.0 and NGINX

	private int poolAcquireTimeoutMillis = 3 * 1000;

	private final Map<URL, HttpHost> hostMap = new HashMap<URL, HttpHost>();

	private AuthCache authCache; //preemptive BASIC

	/**
	 * Copy constructor
	 */
	public HttpClient4Config(HttpClient4Config from) {
		super(from);
		this.poolReleaseTimeoutMillis = from.getPoolReleaseTimeoutMillis();
		this.poolAcquireTimeoutMillis = from.getPoolAcquireTimeoutMillis();
		this.authCache = from.getAuthCache(); //not a deep copy :(
	}

	public HttpClient4Config(String url) {
		super(url);
	}

	public HttpClient4Config(URL url) {
		super(url);
	}

	public HttpClient4Config(HttlTarget target) {
		super(target);
	}

	@Override
	public HttpClient4Transport build() {
		return new HttpClient4Transport(new HttpClient4Config(this));
	}

	@Override
	public HttpClient4Config getSelf() {
		return this;
	}

	/**
	 * Can return null
	 */
	public AuthCache getAuthCache() {
		return authCache;
	}

	public HttpClient newHttpClient() {
		HttpParams httpParams = new BasicHttpParams();

		URL[] urls = super.getTarget().getUrls();
		for (URL url : urls) {
			hostMap.put(url, new HttpHost(url.getHost(), url.getPort(), url.getProtocol()));
		}

		buildClientParams(httpParams);
		buildConnectionParams(httpParams);
		buildProtocolParams(httpParams);
		ClientConnectionManager connectionManager = buildConnectionManager(urls);

		DefaultHttpClient httpClient = new DefaultHttpClient(connectionManager, httpParams);

		buildAuthentication(httpClient);

		return httpClient;
	}

	private void buildAuthentication(DefaultHttpClient httpClient) {
		if (getAuthentication() != null) {
			Authentication authentication = getAuthentication();
			UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(authentication.getUsername(),
					authentication.getPassword());
			CredentialsProvider provider = httpClient.getCredentialsProvider();

			URL[] urls = super.getTarget().getUrls();

			for (URL url : urls) {
				AuthScope scope = new AuthScope(url.getHost(), url.getPort());
				provider.setCredentials(scope, credentials);
			}

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

				authCache = new BasicAuthCache();
				for (URL url : urls) {
					HttpHost host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
					authCache.put(host, scheme);
				}
			}
		}
	}

	protected HttpHost getHttpHost() {
		URL url = getTarget().getUrl();
		return hostMap.get(url);
	}

	protected ClientParamBean buildClientParams(HttpParams httpParams) {
		ClientParamBean clientBean = new ClientParamBean(httpParams);
		clientBean.setConnectionManagerTimeout(this.poolAcquireTimeoutMillis);//httpParams.setParameter(ClientPNames.CONN_MANAGER_TIMEOUT, 5000L);
		clientBean.setHandleRedirects(getFollowRedirects());//ClientPNames.HANDLE_REDIRECTS

		//HttpHost httpHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
		//clientBean.setDefaultHost(httpHost); //ClientPNames.DEFAULT_HOST

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
		protocolBean.setContentCharset(getCharset());
		protocolBean.setHttpElementCharset(getCharset());
		//paramsBean.setUseExpectContinue(true);

		return protocolBean;
	}

	protected PoolingClientConnectionManager buildConnectionManager(URL[] urls) {
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

		SSLContext sslContext = getSslContext();
		if (sslContext != null) {
			SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslContext);
			schemeRegistry.register(new Scheme("https", 443, sslSocketFactory));
		} else {
			schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSystemSocketFactory()));
		}

		//we access only one host
		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry,
				this.poolReleaseTimeoutMillis, TimeUnit.MILLISECONDS);
		connectionManager.setMaxTotal(getPoolMaximumSize() * urls.length);
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

	@Override
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
