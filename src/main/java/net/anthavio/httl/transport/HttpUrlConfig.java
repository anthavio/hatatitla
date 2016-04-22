package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;

/**
 * -Dhttp.keepAlive=true
 * -Dhttp.maxConnections=200
 * -Dsun.net.http.errorstream.enableBuffering=true
 * 
 * @author martin.vanek
 *
 */
public class HttpUrlConfig extends BaseTransportBuilder<HttpUrlConfig> {

	/**
	 * Copy constructor
	 */
	public HttpUrlConfig(HttpUrlConfig from) {
		super(from);
	}

	public HttpUrlConfig(String url) {
		super(url);
	}

	public HttpUrlConfig(URL url) {
		super(url);
	}

	public HttpUrlConfig(HttlTarget target) {
		super(target);
	}

	@Override
	public HttpUrlTransport build() {
		return new HttpUrlTransport(new HttpUrlConfig(this));
	}

	@Override
	public HttpUrlConfig getSelf() {
		return this;
	}

}
