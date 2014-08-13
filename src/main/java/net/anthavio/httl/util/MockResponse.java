package net.anthavio.httl.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.HttlHeaders;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockResponse extends HttlResponse {

	private static final long serialVersionUID = 1L;

	private boolean closed;

	private final byte[] responseBytes;

	public MockResponse(HttlRequest request, int httpCode, String httpMessage, HttlHeaders headers, InputStream stream) {
		super(null, httpCode, httpMessage, headers, null);
		try {
			responseBytes = read(stream);
		} catch (IOException iox) {
			throw new IllegalStateException("Failed to read stream", iox);
		}
	}

	// binary response test
	public MockResponse(HttlRequest request, int httpCode, String contentType, byte[] responseBody) {
		super(null, httpCode, "OK", buildHeaders(contentType), null);
		this.responseBytes = responseBody;
	}

	public MockResponse(HttlRequest request, int httpCode, String contentType, String responseBody) {
		this(request, httpCode, "OK", buildHeaders(contentType), responseBody);
	}

	private static HttlHeaders buildHeaders(String contentType) {
		HttlHeaders headers = new HttlHeaders();
		headers.set("Content-Type", contentType);
		return headers;
	}

	public MockResponse(HttlRequest request, int httpCode, String httpMessage, HttlHeaders headers, String responseBody) {
		super(request, httpCode, httpMessage, headers, null);
		this.responseBytes = responseBody.getBytes(Charset.forName("utf-8"));
	}

	public MockResponse(HttlRequest request, int httpCode, String httpMessage, HttlHeaders headers, byte[] responseBody) {
		super(null, httpCode, httpMessage, headers, null);
		this.responseBytes = responseBody;
	}

	@Override
	public InputStream getStream() {
		return new ByteArrayInputStream(responseBytes);
	}

	@Override
	public Reader getReader() {
		return new InputStreamReader(getStream(), getCharset());
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
