package com.anthavio.hatatitla;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.hatatitla.Authentication.Scheme;
import com.anthavio.hatatitla.SenderBodyRequest.FakeStream;
import com.anthavio.hatatitla.inout.RequestBodyMarshaller;

/**
 * Simple java HttpURLConnection implementation of the HttpSender. No additional library is required
 * 
 * @author martin.vanek
 *
 */
public class HttpURLSender extends HttpSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSenderConfig config;

	private String basicAuthHeader;

	private HttpURLConnection connection;

	public HttpURLSender(String baseUrl) {
		this(new HttpURLConfig(baseUrl));
	}

	public HttpURLSender(HttpURLConfig config) {
		super(config);
		this.config = config;

		if (config.getAuthentication() != null) {
			final Authentication authentication = config.getAuthentication();
			if (authentication.getScheme() == Scheme.BASIC && authentication.getPreemptive()) {
				//we can use preemptive shortcut only for basic authentication
				byte[] bytes = (authentication.getUsername() + ":" + authentication.getPassword()).getBytes(Charset
						.forName(config.getEncoding()));
				String encoded = Base64.encodeBase64String(bytes);
				this.basicAuthHeader = "Basic " + encoded;
			} else {
				//for other authentication schemas use standard java Authenticator
				//http://docs.oracle.com/javase/7/docs/technotes/guides/net/http-auth.html
				Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(authentication.getUsername(), authentication.getPassword().toCharArray());
					}
				});
			}
		}
		//Great way to configure stuff...
		System.setProperty("http.keepAlive", "true");
		System.setProperty("http.maxConnections", String.valueOf(config.getPoolMaximumSize()));
	}

	@Override
	public void close() {
		if (connection != null) {
			try {
				connection.disconnect();
			} catch (Exception x) {
				//ignore
			}
		}
	}

	@Override
	public SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {

		URL url = new URL(config.getHostUrl().getProtocol(), config.getHostUrl().getHost(), config.getHostUrl().getPort(),
				path);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		this.connection = connection;

		connection.setUseCaches(false);
		connection.setDoInput(true);

		Multival headers = request.getHeaders();

		if (headers != null && headers.size() > 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					connection.setRequestProperty(name, value);
				}
			}
		}

		connection.setConnectTimeout(config.getConnectTimeoutMillis());
		if (request.getReadTimeoutMillis() != null) {
			connection.setReadTimeout(request.getReadTimeoutMillis());
		} else {
			connection.setReadTimeout(config.getReadTimeoutMillis());
		}

		connection.setInstanceFollowRedirects(config.getFollowRedirects());

		if (config.getGzipRequest()) {
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
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
				connection.setRequestProperty("Content-Type", contentType);
			}
		}

		if (request.getFirstHeader("Accept") == null && config.getDefaultAccept() != null) {
			connection.setRequestProperty("Accept", config.getDefaultAccept());
		}

		if (request.getFirstHeader("Accept-Charset") == null) {
			connection.setRequestProperty("Accept-Charset", config.getEncoding());
		}

		if (this.basicAuthHeader != null) {
			this.logger.debug("Authorization: " + this.basicAuthHeader);
			connection.setRequestProperty("Authorization", this.basicAuthHeader);
		}

		connection.setRequestMethod(request.getMethod().toString());
		switch (request.getMethod()) {
		case GET:
		case DELETE:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", connection.getRequestProperties());
			}
			break;
		case POST:
		case PUT:
			//set "Content-Type" if not explicitly set by parameters
			if (headers == null || headers.get("Content-Type") == null) {
				connection.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded; charset=" + config.getEncoding());
			}

			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", connection.getRequestProperties());
			}

			connection.setDoOutput(true);

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
						writeBytes(connection, dataBytes);
					} else {
						RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
						}
						if (fake.isStreaming()) {
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						} else {
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						}
					}
				} else {
					writeStream(connection, stream);
				}

			} else if (query != null && query.length() != 0) {
				//POST/PUT without body but with parameters
				byte[] dataBytes = query.getBytes(charset);
				writeBytes(connection, dataBytes);
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		int responseCode;
		try {
			responseCode = connection.getResponseCode();
		} catch (Exception x) {
			throw translateException(connection, x);
		}

		String responseMessage = connection.getResponseMessage();

		Map<String, List<String>> headerFields = connection.getHeaderFields();
		if (this.logger.isDebugEnabled()) {
			if (this.logger.isDebugEnabled()) {
				logHeaders("Response", headerFields);
			}
		}
		Multival responseHeaders = new Multival(headerFields);

		InputStream responseStream = null;
		try {
			responseStream = connection.getInputStream();
		} catch (IOException iox) {
			return new HttpURLResponse(responseCode, responseMessage, responseHeaders, connection.getErrorStream(),
					connection);
		}
		return new HttpURLResponse(responseCode, responseMessage, responseHeaders, responseStream, connection);
	}

	private void writeStream(HttpURLConnection connection, InputStream input) throws IOException {
		DataOutputStream output = null;
		try {
			output = new DataOutputStream(connection.getOutputStream());
			byte[] buffer = new byte[512];
			int read = -1;
			while ((read = input.read(buffer)) != -1) {
				output.write(buffer, 0, read);
			}
			output.flush();
			output.close();//XXX really close?
		} catch (IOException iox) {
			throw translateException(connection, iox, output);
		}
	}

	private void writeBytes(HttpURLConnection connection, byte[] dataBytes) throws IOException {
		connection.setRequestProperty("Content-Length", Integer.toString(dataBytes.length));
		//if (this.logger.isDebugEnabled()) {
		//	logHeaders("Request", connection.getRequestProperties());
		//}
		DataOutputStream output = null;
		try {
			output = new DataOutputStream(connection.getOutputStream());
			output.write(dataBytes);
			output.flush();
			output.close(); //XXX really close?
		} catch (IOException iox) {
			throw translateException(connection, iox, output);
		}
	}

	private IOException translateException(HttpURLConnection connection, Exception exception, OutputStream stream)
			throws IOException {
		try {
			stream.close();
		} catch (Exception x) {
			//ignore
		}
		return translateException(connection, exception);
	}

	private IOException translateException(HttpURLConnection connection, Exception exception) throws IOException {
		if (exception instanceof SocketTimeoutException) {
			//enhance message with timeout values
			if (exception.getMessage().equals("connect timed out")) {
				ConnectException cx = new ConnectException("Connect timeout " + connection.getConnectTimeout() + " ms");
				cx.setStackTrace(exception.getStackTrace());
				throw cx;
			} else if (exception.getMessage().equals("Read timed out")) {
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + connection.getReadTimeout() + " ms");
				stx.setStackTrace(exception.getStackTrace());
				throw stx;
			} else {
				throw (SocketTimeoutException) exception;
			}
		} else if (exception instanceof ConnectException) {
			//enhance message with url
			ConnectException ctx = new ConnectException("Connection refused " + config.getHostUrl());
			ctx.setStackTrace(exception.getStackTrace());
			throw ctx;
		} else if (exception instanceof IOException) {
			throw (IOException) exception;
		} else {
			IOException iox = new IOException(exception.getMessage());
			iox.setStackTrace(exception.getStackTrace());
			throw iox;
		}
	}

	private void logHeaders(String string, Map<String, List<String>> requestProperties) {
		this.logger.debug(string + " Headers");
		String direction = string.equals("Request") ? ">> " : "<< ";
		for (Entry<String, List<String>> entry : requestProperties.entrySet()) {
			List<String> values = entry.getValue();
			for (String value : values) {
				this.logger.debug(direction + entry.getKey() + ": " + value);
			}
		}
	}

	@Override
	public String toString() {
		return "URLHttpSender [" + config.getHostUrl() + "]";
	}
}
