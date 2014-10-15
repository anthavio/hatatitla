package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

/**
 * -Dhttp.keepAlive=true
 * -Dhttp.maxConnections=200
 * -Dsun.net.http.errorstream.enableBuffering=true
 * 
 * @author martin.vanek
 *
 */
public class NingConfig extends BaseTransBuilder<NingConfig> {

	public NingConfig(String url) {
		super(url);
	}

	public NingConfig(URL url) {
		super(url);
	}

	@Override
	public NingTransport build() {
		return new NingTransport(this);
	}

	@Override
	public NingConfig getSelf() {
		return this;
	}

}
