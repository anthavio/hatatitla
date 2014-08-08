package net.anthavio.httl.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.anthavio.httl.HttlBody;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.ReaderInputStream;

import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpFields.Field;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty 8 based sender - DO NOT USE IT !!! It is bag is sh*t, hangs on almost anything
 * 
 * http://wiki.eclipse.org/Jetty/Tutorial/HttpClient
 * 
 * @author martin.vanek
 *
 */
@Deprecated
public class JettyTransport implements HttlTransport {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final JettySenderConfig config;

	private HttpClient client;

	public JettyTransport(String baseUrl) {
		this(new JettySenderConfig(baseUrl));
	}

	public JettyTransport(JettySenderConfig config) {
		this.config = config;
		this.client = config.buildHttpClient();
		try {
			client.start();
		} catch (Exception x) {
			throw new RuntimeException("Failed to start client", x);
		}
	}

	public JettyTransport(String baseUrl, ExecutorService executor) {
		this(new JettySenderConfig(baseUrl), executor);
	}

	public JettyTransport(JettySenderConfig config, ExecutorService executor) {
		this.config = config;
		this.client = config.buildHttpClient();
		try {
			client.start();
		} catch (Exception x) {
			throw new RuntimeException("Failed to start client", x);
		}
	}

	@Override
	public void close() {
		try {
			client.stop();
		} catch (Exception x) {
			throw new RuntimeException("Failed to stop client", x);
		}

	}

	@Override
	public HttlResponse call(HttlRequest request) throws IOException {
		JettyContentExchange exchange = new JettyContentExchange(true);
		exchange.setURL(config.getUrl() + request.getPathAndQuery());
		/*
		exchange.setMethod(request.getMethod().name());
		exchange.setScheme(config.getHostUrl().getProtocol());
		exchange.setAddress(new Address(config.getHostUrl().getHost(), config.getHostUrl().getPort()));
		exchange.setRequestURI(path);
		*/
		HttpHeaders headers = request.getHeaders();

		if (headers != null && headers.size() > 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					exchange.addRequestHeader(name, value);
				}
			}
		}

		if (request.getReadTimeoutMillis() != null) {
			exchange.setTimeout(request.getReadTimeoutMillis());
		} else {
			exchange.setTimeout(config.getReadTimeoutMillis());
		}

		if (request.getBody() != null) {
			String contentType = request.getFirstHeader("Content-Type");
			if (contentType == null) {
				throw new IllegalArgumentException("Request with body must have Content-Type header specified");
			}
			//add charset into Content-Type header if missing
			int idxCharset = contentType.indexOf("charset=");
			if (idxCharset == -1) {
				contentType = contentType + "; charset=" + config.getCharset();
				exchange.setRequestHeader("Content-Type", contentType);
			}
		}

		switch (request.getMethod()) {
		case GET:
		case DELETE:
		case HEAD:
		case OPTIONS:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", exchange.getRequestFields());
			}
			break;
		case POST:
		case PUT:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", exchange.getRequestFields());
			}

			if (request.getBody() != null) {
				HttlBody body = request.getBody();
				switch (body.getType()) {
				case MARSHALL:
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					config.getMarshaller().write(body.getPayload(), baos, Charset.forName(request.getCharset()));
					exchange.setRequestContent(new ByteArrayBuffer(baos.toByteArray()));
					break;
				case STRING:
					String string = (String) body.getPayload();
					byte[] sbytes = string.getBytes(Charset.forName(request.getCharset()));
					exchange.setRequestContent(new ByteArrayBuffer(sbytes));
					break;
				case BYTES:
					byte[] abytes = (byte[]) body.getPayload();
					exchange.setRequestContent(new ByteArrayBuffer(abytes));
					break;
				case STREAM:
					exchange.setRequestContentSource((InputStream) body.getPayload());
					break;
				case READER:
					exchange.setRequestContentSource(new ReaderInputStream((Reader) body.getPayload()));
					break;
				default:
					throw new IllegalStateException("Unsupported HttlBody type: " + body.getType());
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}
		exchange.setURL(config.getUrl() + request.getPathAndQuery());
		client.send(exchange);

		try {
			exchange.waitForDone();
		} catch (InterruptedException ix) {
			throw new RuntimeException("Interrupted while calling " + request, ix);
		}

		if (exchange.getException() != null) {
			//exchange.getException().printStackTrace();
			Throwable x = exchange.getException();
			if (x instanceof ConnectException) {
				throw (ConnectException) x;
			} else if (x instanceof SocketTimeoutException) {
				ConnectException cx = new ConnectException("Connect timeout " + config.getConnectTimeoutMillis() + " ms");
				cx.setStackTrace(x.getStackTrace());
				throw cx;
				//throw (SocketTimeoutException) x;
			} else if (x instanceof IOException) {
				throw (IOException) x;
			} else {
				throw new RuntimeException(x);
			}
		} else {
			return new JettyResponse(request, exchange);
		}

	}

	private void logHeaders(String string, HttpFields headers) {
		this.logger.debug(string + " Headers");
		String direction = string.equals("Request") ? ">> " : "<< ";
		for (int i = 0; i < headers.size(); ++i) {
			Field field = headers.getField(i);
			this.logger.debug(direction + field.getName() + ": " + field.getValue());
		}

	}

	public class JettyContentExchange extends ContentExchange {

		private int httpStatus;

		private String message;

		private HttpFields responseHeaders;

		private byte[] responseBody;

		private Throwable exception;

		public JettyContentExchange(boolean cacheFields) {
			super(cacheFields);
		}

		public int getHttpStatus() {
			return httpStatus;
		}

		public String getMessage() {
			return message;
		}

		public HttpFields getResponseHeaders() {
			return responseHeaders;
		}

		public byte[] getResponseBody() {
			return responseBody;
		}

		public Throwable getException() {
			return exception;
		}

		@Override
		protected synchronized void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
			super.onResponseStatus(version, status, reason);
			this.httpStatus = status;
			this.message = reason.toDetailString();
		}

		@Override
		protected void onResponseHeaderComplete() throws IOException {
			//super.onResponseHeaderComplete();
			if (logger.isDebugEnabled()) {
				logHeaders("Response", getResponseFields());
			}
		}

		@Override
		protected void onConnectionFailed(Throwable x) {
			this.exception = x;
		}

		@Override
		protected void onExpire() {
			this.exception = new ConnectException("Connect timeout " + config.getUrl().toString());
		}

		@Override
		protected void onResponseComplete() throws IOException {
			responseHeaders = getResponseFields();
			responseBody = getResponseContentBytes();
			//super.onResponseComplete();
		}

		@Override
		protected void onException(Throwable x) {
			this.exception = x;
		}
	}

}
