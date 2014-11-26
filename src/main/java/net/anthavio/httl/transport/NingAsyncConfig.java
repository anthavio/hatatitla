package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class NingAsyncConfig extends BaseTransBuilder<NingAsyncConfig> {

	public NingAsyncConfig(String url) {
		super(url);
	}

	public NingAsyncConfig(URL url) {
		super(url);
	}

	@Override
	public NingAsyncTransport build() {
		return new NingAsyncTransport(this);
	}

	@Override
	public NingAsyncConfig getSelf() {
		return this;
	}

}
