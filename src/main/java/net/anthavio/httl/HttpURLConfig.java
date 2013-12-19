package net.anthavio.httl;

import java.net.URL;

/**
 * -Dhttp.keepAlive=true
 * -Dhttp.maxConnections=200
 * -Dsun.net.http.errorstream.enableBuffering=true
 * 
 * @author martin.vanek
 *
 */
public class HttpURLConfig extends HttpSenderConfig {

	public HttpURLConfig(String urlString) {
		super(urlString);
	}

	public HttpURLConfig(URL url) {
		super(url);
	}

	@Override
	public HttpURLSender buildSender() {
		return new HttpURLSender(this);
	}

}
