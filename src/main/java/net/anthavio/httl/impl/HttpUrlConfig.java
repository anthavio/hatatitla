package net.anthavio.httl.impl;

import java.net.URL;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.SenderBuilder;

/**
 * -Dhttp.keepAlive=true
 * -Dhttp.maxConnections=200
 * -Dsun.net.http.errorstream.enableBuffering=true
 * 
 * @author martin.vanek
 *
 */
public class HttpUrlConfig extends SenderBuilder {

	private HttpUrlTransport transport;

	public HttpUrlConfig(String urlString) {
		super(urlString);
	}

	public HttpUrlConfig(URL url) {
		super(url);
	}

	@Override
	public HttlSender build() {
		transport = new HttpUrlTransport(this);
		return new HttlSender(this, transport);
	}

	public HttpUrlTransport getTransport() {
		return transport;
	}

}
