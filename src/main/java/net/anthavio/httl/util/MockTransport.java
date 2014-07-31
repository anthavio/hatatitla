package net.anthavio.httl.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.PseudoStream;
import net.anthavio.httl.inout.RequestMarshaller;

/**
 * Sometimes we need to test what is sent remote server without actualy sending it...
 * 
 * @author martin.vanek
 *
 */
public class MockTransport implements HttlTransport {

	private HttlRequest lastRequest; //from last doExecute invocation
	private HttlResponse lastResponse; //from last doExecute invocation

	private HttlResponse staticResponse; //what to return from doExecute

	private IOException exception; //throw from doExecute

	private boolean closed;

	private AtomicInteger executions = new AtomicInteger(0);

	public MockTransport() {
		this((HttlResponse) null);
	}

	public MockTransport(HttlResponse response) {
		this.staticResponse = response;
	}

	public MockTransport(String responseBody) {
		this(200, "text/plain", responseBody);
	}

	public MockTransport(int responseCode, String contentType, String responseBody) {
		setStaticResponse(responseCode, contentType, responseBody);
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
	public HttlResponse call(HttlRequest request) throws IOException {
		if (closed) {
			throw new IllegalStateException("Sender is closed");
		}
		this.lastRequest = request;
		executions.incrementAndGet();
		if (exception != null) {
			throw exception;
		}
		if (this.staticResponse != null) {
			this.lastResponse = staticResponse;
			return this.staticResponse;
		} else {
			MockResponse response;
			//copy request into response
			if (request.getBodyStream() != null) {
				InputStream stream = request.getBodyStream();
				if (stream instanceof PseudoStream) {
					Object value = ((PseudoStream) stream).getValue();
					if (value instanceof String) {
						response = new MockResponse(request, 200, "OK", request.getHeaders(), (String) value);
					} else if (value instanceof byte[]) {
						response = new MockResponse(200, "OK", request.getHeaders(), (byte[]) value);
					} else {
						//object or byte array inside
						String contentType = request.getFirstHeader(HttlConstants.Content_Type);
						Object[] type = HttpHeaderUtil.splitContentType(contentType, "utf-8");
						String mimeType = (String) type[0];
						//Charset charset = (Charset) type[1];
						RequestMarshaller marshaller = request.getSender().getConfig().getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
						}
						String marshalled = marshaller.marshall(value);
						response = new MockResponse(request, 200, "OK", request.getHeaders(), marshalled);
					}

				} else if (stream != null) {
					response = new MockResponse(200, "OK", request.getHeaders(), stream);
				} else {
					String responseBody = "MockResponse to " + request.getMethod() + " " + request.getPathAndQuery()
							+ " with null body";
					response = new MockResponse(request, 200, "OK", request.getHeaders(), responseBody);
				}

			} else {
				//No request body, but it still would be nice to return something according to Accept header
				String responseBody = "MockResponse to " + request.getMethod() + " " + request.getPathAndQuery();
				response = new MockResponse(request, 200, "OK", request.getHeaders(), responseBody);
			}
			this.lastResponse = response;
			return response;
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
	public void setStaticResponse(int code, String contentType, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", contentType);
		this.staticResponse = new MockResponse(null, code, "OK", headers, body);
	}

	public void setStaticResponse(HttlResponse response) {
		this.staticResponse = response;
	}

	/**
	 * @return Response returned from doExecute
	 */
	public HttlResponse getStaticResponse() {
		return staticResponse;
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
	public HttlRequest getLastRequest() {
		return lastRequest;
	}

	/**
	 * @return response from last doExecute invocation
	 */
	public HttlResponse getLastResponse() {
		return lastResponse;
	}

}
