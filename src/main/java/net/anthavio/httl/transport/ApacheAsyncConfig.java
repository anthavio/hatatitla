package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class ApacheAsyncConfig extends BaseTransBuilder<ApacheAsyncConfig> {

	public ApacheAsyncConfig(String url) {
		super(url);
	}

	public ApacheAsyncConfig(URL url) {
		super(url);
	}

	@Override
	public ApacheAsyncTransport build() {
		return new ApacheAsyncTransport(this);
	}

	@Override
	public ApacheAsyncConfig getSelf() {
		return this;
	}

}
