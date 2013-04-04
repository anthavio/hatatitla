package com.anthavio.hatatitla;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sometimes we need to test what is sent remote server without actualy sending it...
 * 
 * @author martin.vanek
 *
 */
public class FakeSender extends HttpSender {

	private SenderRequest request; //from last doExecute invocation
	private String path;//from last doExecute invocation
	private String query;//from last doExecute invocation

	private SenderResponse response; //what to return form doExecute

	private boolean closed;

	private AtomicInteger executions = new AtomicInteger(0);

	public FakeSender(SenderResponse response) {
		super(new URLHttpConfig("http://never.really.sent.anywhere/"));
		if (response == null) {
			throw new IllegalArgumentException("response is null");
		}
		this.response = response;
	}

	public FakeSender(String responseBody) {
		this(200, responseBody);
	}

	public FakeSender(int responseCode, String responseBody) {
		super(new URLHttpConfig("http://never.really.sent.anywhere/"));
		setResponse(responseCode, responseBody);
	}

	/**
	 * Test Sender closing
	 */
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void close() throws IOException {
		this.closed = true;
	}

	@Override
	protected SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {
		if (closed) {
			throw new IllegalStateException("Sender is closed");
		}
		this.request = request;
		this.path = path;
		this.query = query;
		executions.incrementAndGet();
		return this.response;
	}

	/**
	 * Change Response returned from doExecute
	 */
	public void setResponse(int code, String body) {
		try {
			this.response = new FakeResponse(code, body);
		} catch (IOException iox) {
			throw new IllegalArgumentException("What the hell?!?", iox);
		}
	}

	/**
	 * @return Response returned from doExecute
	 */
	public SenderResponse getResponse() {
		return response;
	}

	/**
	 * Test for number of executions
	 */
	public int getExecutionCount() {
		return executions.intValue();
	}

	/**
	 * @return request from last doExecute invocation
	 */
	public SenderRequest getRequest() {
		return request;
	}

	/**
	 * @return url path from last doExecute invocation
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return url query from last doExecute invocation
	 */
	public String getQuery() {
		return query;
	}

	/**
	 * Response from FakeSender
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class FakeResponse extends SenderResponse {

		private static final long serialVersionUID = 1L;

		private byte[] bodyBytes;

		private boolean closed;

		public FakeResponse(int code, String body) throws IOException {
			super(code, "fake " + code + " http response", null, null);
			this.bodyBytes = body.getBytes(Charset.forName("utf-8"));
		}

		public InputStream getStream() {
			return new ByteArrayInputStream(bodyBytes);
		}

		@Override
		public void close() throws IOException {
			this.closed = true;
		}

		public boolean isClosed() {
			return closed;
		}

	}

}
