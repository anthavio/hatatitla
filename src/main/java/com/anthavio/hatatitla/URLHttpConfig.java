package com.anthavio.hatatitla;

/**
 * -Dhttp.keepAlive=true
 * -Dhttp.maxConnections=200
 * -Dsun.net.http.errorstream.enableBuffering=true
 * 
 * @author martin.vanek
 *
 */
public class URLHttpConfig extends HttpSenderConfig {

	public URLHttpConfig(String urlString) {
		super(urlString);
	}

	@Override
	public URLHttpSender buildSender() {
		return new URLHttpSender(this);
	}

}
