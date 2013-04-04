package com.anthavio.hatatitla;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.hatatitla.SenderRequest.ValueStrategy;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class HttpSenderConfig {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final URL hostUrl; //only protocol, hostname, port (NO path/file/query/anchor)

	private Authentication authentication;

	private String encoding = "utf-8";

	private Charset charset = Charset.forName(this.encoding);

	private boolean followRedirects = false;

	private boolean gzipRequest = false;

	private String defaultAccept; //default Accept header

	private int poolMaximumSize = 10;

	private int connectTimeoutMillis = 5 * 1000; //in millis

	private int readTimeoutMillis = 20 * 1000; //in millis

	private ValueStrategy nullValueStrategy = ValueStrategy.KEEP;

	private ValueStrategy emptyValueStrategy = ValueStrategy.KEEP;

	public HttpSenderConfig(String urlString) {
		if (Cutils.isBlank(urlString)) {
			throw new IllegalArgumentException("URL is blank");
		}
		if (urlString.startsWith("http") == false) {
			urlString = "http://" + urlString;
		}
		//try to parse
		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException("URL is invalid " + urlString, mux);
		}
		if (Cutils.isBlank(url.getHost())) {
			throw new IllegalArgumentException("URL has no host " + urlString);
		}

		String file = url.getFile();
		if ((file != null && (!file.equals("") && !file.equals("/"))) || !Cutils.isEmpty(url.getQuery())) {
			logger.warn("Path and query information is discarded from url " + urlString);
		}
		//construct URL without the file (path + query) part
		try {
			url = new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException(mux);
		}
		this.hostUrl = url;
	}

	public abstract HttpSender buildSender();

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
		this.charset = Charset.forName(encoding);
	}

	public Charset getCharset() {
		return this.charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
		this.encoding = charset.name();
	}

	public int getConnectTimeoutMillis() {
		return this.connectTimeoutMillis;
	}

	public void setConnectTimeoutMillis(int millis) {
		this.connectTimeoutMillis = millis;
	}

	public int getReadTimeoutMillis() {
		return this.readTimeoutMillis;
	}

	public void setReadTimeoutMillis(int millis) {
		this.readTimeoutMillis = millis;
	}

	public URL getHostUrl() {
		return this.hostUrl;
	}

	public int getPoolMaximumSize() {
		return poolMaximumSize;
	}

	public void setPoolMaximumSize(int poolMaximum) {
		this.poolMaximumSize = poolMaximum;
	}

	public Authentication getAuthentication() {
		return this.authentication;
	}

	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}

	public boolean getGzipRequest() {
		return gzipRequest;
	}

	public void setGzipRequest(boolean gzipRequest) {
		this.gzipRequest = gzipRequest;
	}

	public String getDefaultAccept() {
		return defaultAccept;
	}

	public void setDefaultAccept(String acceptType) {
		this.defaultAccept = acceptType;
	}

	public boolean getFollowRedirects() {
		return followRedirects;
	}

	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	public ValueStrategy getNullValueStrategy() {
		return nullValueStrategy;
	}

	public void setNullValueStrategy(ValueStrategy nullValueStrategy) {
		this.nullValueStrategy = nullValueStrategy;
	}

	public ValueStrategy getEmptyValueStrategy() {
		return emptyValueStrategy;
	}

	public void setEmptyValueStrategy(ValueStrategy emptyValueStrategy) {
		this.emptyValueStrategy = emptyValueStrategy;
	}

}
