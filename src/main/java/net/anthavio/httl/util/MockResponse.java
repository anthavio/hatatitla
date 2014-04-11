package net.anthavio.httl.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockResponse extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private boolean closed;

	public MockResponse(int httpCode, String contentType, String body) {
		this(httpCode, new Multival(), body);
		super.getHeaders().set("Content-Type", contentType);
		String[] strings = HttpHeaderUtil.splitContentType(contentType, encoding);
		super.mediaType = strings[0];
		super.encoding = strings[1];
	}

	public MockResponse(int httpCode, Multival headers, String responseBody) {
		super(httpCode, "MockResponse: " + httpCode + " http response", headers, toStream(responseBody));
	}

	public MockResponse(int httpCode, String message, Multival headers, InputStream stream) {
		super(httpCode, message, headers, stream);
	}

	public MockResponse(int httpCode, String message, Multival headers, String responseBody) {
		super(httpCode, message, headers, toStream(responseBody));
	}

	private static InputStream toStream(String string) {
		return new ByteArrayInputStream(string.getBytes(Charset.forName("utf-8")));
	}

	@Override
	public void close() {
		super.close();
		this.closed = true;
	}

	public boolean isClosed() {
		return closed;
	}

}
