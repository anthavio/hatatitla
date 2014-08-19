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
public class HttpUrlConfig extends BaseTransBuilder<HttpUrlConfig> {

	public HttpUrlConfig(String url) {
		super(url);
	}

	public HttpUrlConfig(URL url) {
		super(url);
	}

	@Override
	public HttpUrlTransport build() {
		return new HttpUrlTransport(this);
	}

	@Override
	public HttpUrlConfig getSelf() {
		return this;
	}

}
