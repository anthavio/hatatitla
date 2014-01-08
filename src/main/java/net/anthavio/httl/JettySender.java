package net.anthavio.httl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.anthavio.httl.SenderBodyRequest.FakeStream;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.security.Realm;
import org.eclipse.jetty.client.security.SimpleRealmResolver;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpFields.Field;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;

/**
 * Jetty 8 based sender
 * 
 * http://wiki.eclipse.org/Jetty/Tutorial/HttpClient
 * 
 * @author martin.vanek
 *
 */
public class JettySender extends HttpSender {

	private final HttpSenderConfig config;

	private HttpClient client;

	public JettySender(HttpSenderConfig config) {
		this(config, null);
	}

	public JettySender(HttpSenderConfig config, ExecutorService executor) {
		super(config, executor);
		this.config = config;

		client = new HttpClient();
		client.setConnectBlocking(false);
		client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
		client.setConnectTimeout(config.getConnectTimeoutMillis());
		client.setTimeout(config.getReadTimeoutMillis());
		client.setMaxConnectionsPerAddress(config.getPoolMaximumSize());
		//client.setIdleTimeout(config.get???);

		if (config.getFollowRedirects()) {
			client.setMaxRedirects(10);
		}
		if (config.getAuthentication() != null) {
			Realm realm = new SimpleRealm("whatever", config.getAuthentication().getUsername(), config.getAuthentication()
					.getPassword());
			client.setRealmResolver(new SimpleRealmResolver(realm));
		}
		try {
			client.start();
		} catch (Exception x) {
			throw new RuntimeException("Failed to start client", x);
		}
	}

	@Override
	protected SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {
		JettyContentExchange exchange = new JettyContentExchange(true);
		//exchange.setURL(config.getHostUrl() + path);
		exchange.setMethod(request.getMethod().name());
		exchange.setScheme(config.getHostUrl().getProtocol());
		exchange.setAddress(new Address(config.getHostUrl().getHost(), config.getHostUrl().getPort()));
		exchange.setRequestURI(path);

		Multival headers = request.getHeaders();

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

		if (config.getGzipRequest()) {
			exchange.setRequestHeader("Accept-Encoding", "gzip, deflate");
		}

		if (request.hasBody()) {
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

		if (request.getFirstHeader("Accept") == null && config.getDefaultAccept() != null) {
			exchange.setRequestHeader("Accept", config.getDefaultAccept());
		}

		if (request.getFirstHeader("Accept-Charset") == null) {
			exchange.setRequestHeader("Accept-Charset", config.getEncoding());
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
			//set "Content-Type" if not explicitly set by parameters
			if (headers == null || headers.get("Content-Type") == null) {
				exchange.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=" + config.getEncoding());
			}

			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", exchange.getRequestFields());
			}

			String contentType = request.getFirstHeader("Content-Type");
			Object[] type = HttpHeaderUtil.splitContentType(contentType, config.getCharset());
			String mimeType = (String) type[0];
			Charset charset = (Charset) type[1];
			if (request.hasBody()) {
				InputStream stream = ((SenderBodyRequest) request).getBodyStream();
				if (stream instanceof FakeStream) {
					FakeStream fake = (FakeStream) stream;

					if (fake.getValue() instanceof String) {
						String string = (String) fake.getValue();
						byte[] dataBytes = string.getBytes(charset);
						exchange.setRequestContent(new ByteArrayBuffer(dataBytes));
					} else {
						RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
						}
						String requestContent = marshaller.marshall(fake.getValue());
						exchange.setRequestContentSource(new ByteArrayInputStream(requestContent.getBytes(charset)));
						/*
						if (fake.isStreaming()) {
							//marshaller.write(fake.getValue(), exchange.getOutputStream(), charset);
						} else {
							//XXX create string first an then write...
							//marshaller.write(fake.getValue(), exchange.getOutputStream(), charset);
						}
						*/
					}
				} else {
					exchange.setRequestContentSource(stream);
				}

			} else if (query != null && query.length() != 0) {
				//POST/PUT without body but with parameters
				byte[] dataBytes = query.getBytes(charset);
				exchange.setRequestContent(new ByteArrayBuffer(dataBytes));
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		client.send(exchange);

		try {
			exchange.waitForDone();
		} catch (InterruptedException ix) {
			throw new RuntimeException("Interrupted while calling " + request, ix);
		}

		if (exchange.getException() != null) {
			Throwable x = exchange.getException();
			if (x instanceof ConnectException) {
				throw (ConnectException) x;
			} else if (x instanceof SocketTimeoutException) {
				throw (SocketTimeoutException) x;
			} else if (x instanceof IOException) {
				throw (IOException) x;
			} else {
				throw new RuntimeException(x);
			}
		} else {
			return new JettyResponse(exchange);
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

	@Override
	public void close() {
		try {
			client.stop();
		} catch (Exception x) {
			throw new RuntimeException("Failed to stop client", x);
		}

	}

	public class JettyContentExchange extends ContentExchange {

		private int status;

		private String message;

		private HttpFields responseHeaders;

		private byte[] responseBody;

		private Throwable exception;

		public JettyContentExchange(boolean cacheFields) {
			super(cacheFields);
		}

		public int getStatus() {
			return status;
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
			this.status = status;
			this.message = reason.toDetailString();
		}

		@Override
		protected void onResponseHeaderComplete() throws IOException {
			super.onResponseHeaderComplete();
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
			this.exception = new ConnectException("yadda yadda");
		}

		@Override
		protected void onResponseComplete() throws IOException {
			responseHeaders = getResponseFields();
			responseBody = getResponseContentBytes();
		}

		@Override
		protected void onException(Throwable x) {
			this.exception = x;
		}
	}

	private static class SimpleRealm implements Realm {

		private String id;

		private String principal;

		private String credentials;

		private SimpleRealm(String id, String principal, String credentials) {
			this.id = id;
			this.principal = principal;
			this.credentials = credentials;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getPrincipal() {
			return principal;
		}

		@Override
		public String getCredentials() {
			return credentials;
		}

	}
}
