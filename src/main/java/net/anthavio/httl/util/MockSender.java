package net.anthavio.httl.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.httl.Constants;
import net.anthavio.httl.HttpSender;
import net.anthavio.httl.SenderBodyRequest;
import net.anthavio.httl.SenderBodyRequest.FakeStream;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.inout.RequestBodyMarshaller;

/**
 * Sometimes we need to test what is sent remote server without actualy sending it...
 * 
 * @author martin.vanek
 *
 */
public class MockSender extends HttpSender {

	private SenderRequest lastRequest; //from last doExecute invocation
	private String lastPath;//from last doExecute invocation
	private String lastQuery;//from last doExecute invocation

	private SenderResponse staticResponse; //what to return from doExecute

	private IOException exception; //throw from doExecute

	private boolean closed;

	private AtomicInteger executions = new AtomicInteger(0);

	public MockSender() {
		this((SenderResponse) null);
	}

	public MockSender(SenderResponse response) {
		super(new MockConfig());
		this.staticResponse = response;
	}

	public MockSender(String responseBody) {
		this(200, "text/plain", responseBody);
	}

	public MockSender(int responseCode, String contentType, String responseBody) {
		super(new MockConfig());
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
	protected SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {
		if (closed) {
			throw new IllegalStateException("Sender is closed");
		}
		this.lastRequest = request;
		this.lastPath = path;
		this.lastQuery = query;
		executions.incrementAndGet();
		if (exception != null) {
			throw exception;
		}
		if (this.staticResponse != null) {
			return this.staticResponse;
		} else {
			MockResponse response;
			//copy request into response
			if (request instanceof SenderBodyRequest) {
				InputStream stream = ((SenderBodyRequest) request).getBodyStream();
				if (stream instanceof FakeStream) {
					//XXX what about if value is byte array or so....
					Object value = ((FakeStream) stream).getValue();
					if (value instanceof String) {
						response = new MockResponse(200, "OK", request.getHeaders(), (String) value);
					} else {
						//object or byte array inside
						String contentType = request.getFirstHeader(Constants.Content_Type);
						Object[] type = HttpHeaderUtil.splitContentType(contentType, "utf-8");
						String mimeType = (String) type[0];
						//Charset charset = (Charset) type[1];
						RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
						}
						String marshalled = marshaller.marshall(value);
						response = new MockResponse(200, "OK", request.getHeaders(), marshalled);
					}

				} else {
					response = new MockResponse(200, "OK", request.getHeaders(), stream);
				}

			} else {
				//No request body, but it still would be nice to return something according to Accept header
				String responseBody = "MockResponse to " + request.getMethod() + " " + path;
				response = new MockResponse(200, "OK", request.getHeaders(), responseBody);
			}
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
		Multival headers = new Multival();
		headers.add("Content-Type", contentType);
		this.staticResponse = new MockResponse(code, "OK", headers, body);
	}

	/**
	 * @return Response returned from doExecute
	 */
	public SenderResponse getStaticResponse() {
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
	public SenderRequest getLastRequest() {
		return lastRequest;
	}

	/**
	 * @return url path from last doExecute invocation
	 */
	public String getLastPath() {
		return lastPath;
	}

	/**
	 * @return url query from last doExecute invocation
	 */
	public String getLastQuery() {
		return lastQuery;
	}

}
