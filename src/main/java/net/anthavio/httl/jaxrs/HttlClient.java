package net.anthavio.httl.jaxrs;

import java.net.URI;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.UriBuilder;

/**
 * 
 * @author vanek
 *
 */
public class HttlClient implements Client {

	private Configuration config;

	public HttlClient(Configuration config) {
		this.config = config;
	}

	@Override
	public Configuration getConfiguration() {
		return config;
	}

	@Override
	public Client property(String name, Object value) {
		return this;
	}

	@Override
	public Client register(Class<?> componentClass) {
		return this;
	}

	@Override
	public Client register(Class<?> componentClass, int priority) {
		return this;
	}

	@Override
	public Client register(Class<?> componentClass, Class<?>... contracts) {
		return this;
	}

	@Override
	public Client register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
		return this;
	}

	@Override
	public Client register(Object component) {
		return this;
	}

	@Override
	public Client register(Object component, int priority) {
		return this;
	}

	@Override
	public Client register(Object component, Class<?>... contracts) {
		return this;
	}

	@Override
	public Client register(Object component, Map<Class<?>, Integer> contracts) {
		return this;
	}

	@Override
	public void close() {

	}

	@Override
	public WebTarget target(String uri) {
		return null;
	}

	@Override
	public WebTarget target(URI uri) {
		return null;
	}

	@Override
	public WebTarget target(UriBuilder uriBuilder) {
		return null;
	}

	@Override
	public WebTarget target(Link link) {
		return null;
	}

	@Override
	public Builder invocation(Link link) {
		return null;
	}

	@Override
	public SSLContext getSslContext() {
		return null;
	}

	@Override
	public HostnameVerifier getHostnameVerifier() {
		return null;
	}

}
