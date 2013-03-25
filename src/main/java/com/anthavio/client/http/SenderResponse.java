package com.anthavio.client.http;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderResponse implements Closeable, Serializable {

	private static final long serialVersionUID = 1L;

	private int httpStatusCode;

	private String httpStatusMessage;

	private Multival headers;

	private transient InputStream stream;

	public SenderResponse(int code, String message, Multival headers, InputStream stream) {
		this.httpStatusCode = code;
		this.httpStatusMessage = message;
		this.headers = headers;
		this.stream = stream; //null for 304 Not Modified
	}

	protected SenderResponse() {
		//for serialization
		this.httpStatusCode = 0;
		this.httpStatusMessage = null;
		this.headers = null;
		this.stream = null;
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getHttpStatusMessage() {
		return httpStatusMessage;
	}

	public Multival getHeaders() {
		return headers;
	}

	public String getFirstHeader(String string) {
		if (headers != null) {
			return headers.getFirst(string);
		} else {
			return null;
		}
	}

	public InputStream getStream() {
		return stream;
	}

	public Reader getReader() {
		return new InputStreamReader(stream, getCharset());
	}

	public boolean isBinaryContent() {
		return !HttpHeaderUtil.isTextContent(this);
	}

	public Charset getCharset() {
		return HttpHeaderUtil.getCharset(getFirstHeader("Content-Type"));
	}

	@Override
	public String toString() {
		return "SenderResponse {" + httpStatusCode + ", " + httpStatusMessage + "}";
	}

}
