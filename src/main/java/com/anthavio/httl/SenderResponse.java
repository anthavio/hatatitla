package com.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import com.anthavio.httl.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderResponse implements Closeable, Serializable {

	private static final long serialVersionUID = 1L;

	protected int httpStatusCode;

	protected String httpStatusMessage;

	protected Multival headers;

	protected transient InputStream stream;

	public SenderResponse(int code, String message, Multival headers, InputStream stream) throws IOException {
		this.httpStatusCode = code;
		this.httpStatusMessage = message;
		this.headers = headers;
		String responseEncoding = headers.getFirst("Content-Encoding");
		if (stream != null && responseEncoding != null) {
			if (responseEncoding.indexOf("gzip") != -1) {
				stream = new GZIPInputStream(stream);
			} else if (responseEncoding.indexOf("deflate") != -1) {
				stream = new InflaterInputStream(stream);
			}
		}
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
