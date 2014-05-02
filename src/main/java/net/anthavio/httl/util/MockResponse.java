package net.anthavio.httl.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

	private final byte[] responseBytes;

	public MockResponse(int httpCode, String httpMessage, Multival headers, InputStream stream) {
		super(httpCode, httpMessage, headers, null);
		try {
			responseBytes = read(stream);
		} catch (IOException iox) {
			throw new IllegalStateException("Failed to read stream", iox);
		}
	}

	// binary response test
	public MockResponse(int httpCode, String contentType, byte[] responseBody) {
		super(httpCode, "OK", new Multival(), null);
		super.getHeaders().set("Content-Type", contentType);
		this.responseBytes = responseBody;
	}

	public MockResponse(int httpCode, String contentType, String responseBody) {
		this(httpCode, "OK", new Multival(), responseBody);

		super.getHeaders().set("Content-Type", contentType);
		String[] strings = HttpHeaderUtil.splitContentType(contentType, encoding);
		super.mediaType = strings[0];
		super.encoding = strings[1];
	}

	public MockResponse(int httpCode, String httpMessage, Multival headers, String responseBody) {
		super(httpCode, httpMessage, headers, null);
		this.responseBytes = responseBody.getBytes(Charset.forName("utf-8"));
	}

	public MockResponse(int httpCode, String httpMessage, Multival headers, byte[] responseBody) {
		super(httpCode, httpMessage, headers, null);
		this.responseBytes = responseBody;
	}

	@Override
	public InputStream getStream() {
		return new ByteArrayInputStream(responseBytes);
	}

	@Override
	public void close() {
		super.close();
		this.closed = true;
	}

	public boolean isClosed() {
		return closed;
	}

	private static byte[] read(InputStream stream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read = -1;
		while ((read = stream.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
		return baos.toByteArray();
	}

}
