package net.anthavio.httl.jaxrs;

import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;

public class HttlClientBuilder extends ClientBuilder {

	private Configuration config;

	@Override
	public Client build() {
		return new HttlClient(config);
	}

	@Override
	public Configuration getConfiguration() {
		return config;
	}

	@Override
	public ClientBuilder property(String name, Object value) {
		return this;
	}

	@Override
	public ClientBuilder register(Class<?> componentClass) {
		return this;
	}

	@Override
	public ClientBuilder register(Class<?> componentClass, int priority) {
		return this;
	}

	@Override
	public ClientBuilder register(Class<?> componentClass, Class<?>... contracts) {
		return this;
	}

	@Override
	public ClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
		return this;
	}

	@Override
	public ClientBuilder register(Object component) {
		return this;
	}

	@Override
	public ClientBuilder register(Object component, int priority) {
		return this;
	}

	@Override
	public ClientBuilder register(Object component, Class<?>... contracts) {
		return this;
	}

	@Override
	public ClientBuilder register(Object component, Map<Class<?>, Integer> contracts) {
		return this;
	}

	@Override
	public ClientBuilder withConfig(Configuration config) {
		this.config = config;
		return this;
	}

	@Override
	public ClientBuilder sslContext(SSLContext sslContext) {
		return this;
	}

	@Override
	public ClientBuilder keyStore(KeyStore keyStore, char[] password) {
		return this;
	}

	@Override
	public ClientBuilder trustStore(KeyStore trustStore) {
		return this;
	}

	@Override
	public ClientBuilder hostnameVerifier(HostnameVerifier verifier) {
		return this;
	}

}
