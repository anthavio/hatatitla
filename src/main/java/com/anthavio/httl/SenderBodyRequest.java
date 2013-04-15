package com.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;

import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.util.Cutils;

/**
 * Base class for POST and PUT Requests
 * 
 * @author martin.vanek
 *
 */
public class SenderBodyRequest extends SenderRequest {

	private InputStream bodyStream;

	protected SenderBodyRequest(HttpSender sender, Method method, String urlPath) {
		super(sender, method, urlPath);
	}

	protected SenderBodyRequest(HttpSender sender, Method method, String urlPath, Multival parameters) {
		super(sender, method, urlPath, parameters, null);
	}

	public SenderBodyRequest(HttpSender sender, Method method, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	@Override
	public boolean hasBody() {
		return this.bodyStream != null;
	}

	public SenderRequest setBody(Object bodyObject, String contentType) {
		if (bodyObject == null) {
			throw new IllegalArgumentException("Body object is null");
		}
		FakeStream stream = new FakeStream(bodyObject);
		setBody(stream, contentType);
		return this;
	}

	public SenderRequest setBody(String bodyString, String contentType) {
		if (Cutils.isBlank(bodyString)) {
			throw new IllegalArgumentException("Body string is blank");
		}
		FakeStream stream = new FakeStream(bodyString);
		setBody(stream, contentType);
		return this;
	}

	public SenderRequest setBody(InputStream body, String contentType) {
		if (body == null) {
			throw new IllegalArgumentException("Body stream is null");
		}
		this.bodyStream = body;

		if (Cutils.isBlank(contentType)) {
			throw new IllegalArgumentException("Content-Type is blank");
		}
		if (sender != null) {
			int idxCharset = contentType.indexOf("charset=");
			if (idxCharset == -1) {
				contentType += "; charset=" + sender.getConfig().getEncoding();
			}
		}
		setHeader("Content-Type", contentType);

		return this;
	}

	public InputStream getBodyStream() {
		return this.bodyStream;
	}

	/**
	 * XXX This is quite ugly. What about some RequestBodyWriter abstration...
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class FakeStream extends InputStream {

		private final Object value;

		private final boolean streaming;

		public FakeStream(Object value) {
			this(value, true);
		}

		public FakeStream(Object value, boolean streaming) {
			this.value = value;
			this.streaming = streaming;
		}

		public Object getValue() {
			return value;
		}

		public boolean isStreaming() {
			return streaming;
		}

		@Override
		public void close() throws IOException {
			throw new UnsupportedOperationException("Don't close me!");
		}

		@Override
		public int read() throws IOException {
			throw new UnsupportedOperationException("Don't read me!");
		}

	}

}
