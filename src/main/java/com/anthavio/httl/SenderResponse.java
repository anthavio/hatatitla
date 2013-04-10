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

	protected String mimeType = "mime/unknown";

	protected Charset charset = Charset.forName("ISO-8859-1");

	public SenderResponse(int code, String message, Multival headers, InputStream stream) {
		this.httpStatusCode = code;
		this.httpStatusMessage = message;
		this.headers = headers;
		String responseEncoding = headers.getFirst("Content-Encoding");
		if (stream != null && responseEncoding != null) {
			if (responseEncoding.indexOf("gzip") != -1) {
				try {
					stream = new GZIPInputStream(stream);
				} catch (IOException iox) {
					throw new SenderException(iox);
				}
			} else if (responseEncoding.indexOf("deflate") != -1) {
				stream = new InflaterInputStream(stream);
			}
		}
		this.stream = stream; //null for 304 Not Modified

		String contentType = headers.getFirst("Content-Type");
		if (contentType != null) {
			Object[] parts = HttpHeaderUtil.splitContentType(contentType, charset);
			this.mimeType = (String) parts[0];
			this.charset = (Charset) parts[1];
		}
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
		return !HttpHeaderUtil.isTextContent(mimeType);
	}

	public Charset getCharset() {
		return charset;
	}

	public String getMimeType() {
		return mimeType;
	}

	@Override
	public String toString() {
		return "SenderResponse {" + httpStatusCode + ", " + httpStatusMessage + "}";
	}

}
