package net.anthavio.httl.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import net.anthavio.httl.HttlBody;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;
import net.anthavio.httl.transport.HttlTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sometimes we need to test what is sent remote server without actualy sending it...
 * 
 * @author martin.vanek
 *
 */
public class MockTransport extends BaseTransportBuilder<MockTransport> implements HttlTransport {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private HttlRequest lastRequest; //from last doExecute invocation
	private HttlResponse lastResponse; //from last doExecute invocation

	private HttlResponse staticResponse; //what to return from doExecute

	private IOException exception; //throw from doExecute

	private boolean closed;

	private AtomicInteger executions = new AtomicInteger(0);

	public MockTransport(String url) {
		super(url);
	}

	public MockTransport() {
		this((HttlResponse) null);
	}

	public MockTransport(HttlResponse response) {
		super("http://mock.mock.mock:6363");
		this.staticResponse = response;
	}

	public MockTransport(int responseCode, String contentType, String responseBody) {
		super("http://mock.mock.mock:6363");
		setStaticResponse(responseCode, contentType, responseBody);
	}

	public MockTransport(HttlTarget target) {
		super(target);
	}

	@Override
	public MockTransport getSelf() {
		return this;
	}

	@Override
	public MockTransport getConfig() {
		return this;
	}

	@Override
	public MockTransport build() {
		return this;
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
	public void call(HttlRequest request, HttlTransportCallback callback) {
		HttlResponse response = null;
		try {

			try {
				response = call(request); //blocking execution 
			} catch (HttlRequestException hrx) {
				callback.onRequestFailure(request, hrx);

			} catch (ConnectException cx) {
				callback.onRequestFailure(request, cx);

			} catch (SSLException sslx) {
				callback.onRequestFailure(request, sslx);

			} catch (SocketTimeoutException stx) {
				callback.onResponseFailure(request, stx);

			} catch (Exception x) {
				//For others blame response
				callback.onResponseFailure(request, x);
			}

			callback.onResponse(response);

		} finally {
			Cutils.close(response);
		}
	}

	@Override
	public HttlResponse call(HttlRequest request) throws IOException {
		if (closed) {
			throw new HttlRequestException("Sender is closed");
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
			if (request.getBody() != null) {
				HttlBody body = request.getBody();
				logger.debug("Payload " + body.getType() + " : " + body.getPayload());

				switch (body.getType()) {
				case MARSHALL:
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					request.getSenderConfig().getMarshaller()
							.marshall(request.getBody().getPayload(), request.getMediaType(), request.getCharset(), baos);
					response = new MockResponse(request, 200, "OK", request.getHeaders(), baos.toByteArray());
					break;
				case STRING:
					response = new MockResponse(request, 200, "OK", request.getHeaders(), (String) body.getPayload());
					break;
				case BYTES:
					response = new MockResponse(request, 200, "OK", request.getHeaders(), (byte[]) body.getPayload());
					break;
				case STREAM:
					response = new MockResponse(request, 200, "OK", request.getHeaders(), (InputStream) body.getPayload());
					break;
				case READER:
					response = new MockResponse(request, 200, "OK", request.getHeaders(), new ReaderInputStream(
							(Reader) body.getPayload()));
					break;
				default:
					throw new IllegalStateException("Unsupported HttlBody type: " + body.getType());
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
		Multival<String> headers = new Multival<String>();
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
