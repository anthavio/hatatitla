package net.anthavio.httl.util;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.httl.HttpSender;
import net.anthavio.httl.SenderBodyRequest;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;

/**
 * Sometimes we need to test what is sent remote server without actualy sending it...
 * 
 * @author martin.vanek
 *
 */
public class MockSender extends HttpSender {

	private SenderRequest request; //from last doExecute invocation
	private String path;//from last doExecute invocation
	private String query;//from last doExecute invocation

	private SenderResponse response; //what to return from doExecute

	private IOException exception; //throw from doExecute

	private boolean closed;

	private AtomicInteger executions = new AtomicInteger(0);

	public MockSender() {
		this((SenderResponse) null);
	}

	public MockSender(SenderResponse response) {
		super(new MockConfig());
		this.response = response;
	}

	public MockSender(String responseBody) {
		this(200, "text/plain", responseBody);
	}

	public MockSender(int responseCode, String contentType, String responseBody) {
		super(new MockConfig());
		setResponse(responseCode, contentType, responseBody);
	}

	/**
	 * Test Sender closing
	 */
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void close() {
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
		if (exception != null) {
			throw exception;
		}
		if (this.response != null) {
			return this.response;
		} else {
			//copy request into response
			if (request instanceof SenderBodyRequest) {
				return new MockResponse(200, "OK", request.getHeaders(), ((SenderBodyRequest) request).getBodyStream());
			} else {
				String responseBody = "Response to " + request.getMethod() + " " + path + " " + query;
				return new MockResponse(200, "OK", request.getHeaders(), responseBody);
			}
		}
	}

	/**
	 * Set Exception thrown from doExecute
	 */
	public void setException(IOException exception) {
		this.exception = exception;
	}

	/**
	 * Set Response returned from doExecute
	 */
	public void setResponse(int code, String contentType, String body) {
		Multival headers = new Multival();
		headers.add("Content-Type", contentType);
		this.response = new MockResponse(code, headers, body);
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

}
