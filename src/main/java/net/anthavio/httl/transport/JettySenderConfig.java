package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.SenderBuilder;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.security.Realm;
import org.eclipse.jetty.client.security.SimpleRealmResolver;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * @author martin.vanek
 *
 */
@Deprecated
public class JettySenderConfig extends SenderBuilder {

	private JettyTransport transport;

	public JettySenderConfig(String urlString) {
		super(urlString);
	}

	public JettySenderConfig(URL url) {
		super(url);
	}

	@Override
	public HttlSender build() {
		transport = new JettyTransport(this);
		return new HttlSender(this, transport);
	}

	public JettyTransport getTransport() {
		return transport;
	}

	public HttpClient buildHttpClient() {
		HttpClient client = new HttpClient();
		//client.setConnectBlocking(false);
		client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);

		client.setConnectTimeout(getConnectTimeoutMillis());
		client.setTimeout(getReadTimeoutMillis());

		client.setMaxConnectionsPerAddress(getPoolMaximumSize());
		client.setThreadPool(new QueuedThreadPool(getPoolMaximumSize()));
		//client.setIdleTimeout(config.get???);

		if (getFollowRedirects()) {
			client.setMaxRedirects(10);
		}
		if (getAuthentication() != null) {
			Realm realm = new SimpleRealm("whatever", getAuthentication().getUsername(), getAuthentication().getPassword());
			client.setRealmResolver(new SimpleRealmResolver(realm));
		}

		return client;
	}

	private static class SimpleRealm implements Realm {

		private String id;

		private String principal;

		private String credentials;

		private SimpleRealm(String id, String principal, String credentials) {
			this.id = id;
			this.principal = principal;
			this.credentials = credentials;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getPrincipal() {
			return principal;
		}

		@Override
		public String getCredentials() {
			return credentials;
		}

	}

}
