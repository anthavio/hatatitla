package com.anthavio.client.http;

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

import com.anthavio.client.http.Authentication.Scheme;
import com.anthavio.client.http.SenderBodyRequest.FakeStream;
import com.anthavio.client.http.inout.RequestBodyMarshaller;

/**
 * Simple java HttpUrlConnection implementation of the HttpSender. No additional library is required
 * 
 * @author martin.vanek
 *
 */
public class JavaHttpSender extends HttpSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSenderConfig config;

	private String basicAuthHeader;

	public JavaHttpSender(String baseUrl) {
		this(new HttpSenderConfig(baseUrl));
	}

	public JavaHttpSender(HttpSenderConfig config) {
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
	}

	@Override
	public void close() {
		//nothing can be closed here
	}

	@Override
	public SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {

		URL url = new URL(config.getHostUrl().getProtocol(), config.getHostUrl().getHost(), config.getHostUrl().getPort(),
				path);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		connection.setUseCaches(false);
		connection.setDoOutput(true);
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

		connection.setConnectTimeout(config.getConnectTimeout());
		if (request.getReadTimeout() != null) {
			connection.setReadTimeout(request.getReadTimeout());
		} else {
			connection.setReadTimeout(config.getReadTimeout());
		}

		connection.setInstanceFollowRedirects(config.getFollowRedirects());

		if (config.getCompress()) {
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

			if (request.hasBody()) {
				InputStream stream = ((SenderBodyRequest) request).getBodyStream();
				if (stream instanceof FakeStream) {
					FakeStream fake = (FakeStream) stream;
					switch (fake.getType()) {
					case OBJECT:
						String contentType = request.getFirstHeader("Content-Type");
						Object[] type = HttpHeaderUtil.splitContentType(contentType, config.getCharset());
						String mimeType = (String) type[0];
						Charset charset = (Charset) type[1];
						RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for ");
						}
						if (fake.isStreaming()) {
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						} else {
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						}
						break;
					case STRING:
						String string = (String) fake.getValue();
						byte[] dataBytes2 = string.getBytes(config.getCharset());
						writeBytes(connection, dataBytes2);
						break;
					default:
						throw new IllegalArgumentException("Unsupported FakeType " + fake.getType());
					}
				} else {
					writeStream(connection, stream);
				}

			} else if (query != null && query.length() != 0) {
				//POST/PUT without body but with parameters
				byte[] dataBytes = query.getBytes(config.getCharset());
				writeBytes(connection, dataBytes);
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		int responseCode;
		try {
			responseCode = connection.getResponseCode();
		} catch (SocketTimeoutException stx) {
			throw translateException(connection, stx);
		}

		String responseMessage = connection.getResponseMessage();

		Map<String, List<String>> headerFields = connection.getHeaderFields();
		if (this.logger.isDebugEnabled()) {
			if (this.logger.isDebugEnabled()) {
				logHeaders("Response", headerFields);
			}
		}
		Multival responseHeaders = new Multival(headerFields);

		InputStream inputStream = null;
		try {
			inputStream = connection.getInputStream();
		} catch (IOException iox) {
			return new JavaHttpResponse(responseCode, responseMessage, responseHeaders, connection.getErrorStream(),
					connection);
		}

		return new JavaHttpResponse(responseCode, responseMessage, responseHeaders, inputStream, connection);
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
			if (exception.getMessage().equals("connect timed out")) {
				ConnectException cx = new ConnectException("Connect timeout " + config.getConnectTimeout() + " ms");
				cx.setStackTrace(exception.getStackTrace());
				throw cx;
			} else if (exception.getMessage().equals("Read timed out")) {
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + connection.getReadTimeout() + " ms");
				stx.setStackTrace(exception.getStackTrace());
				throw stx;
			} else {
				throw (SocketTimeoutException) exception;
			}
			//} else if(exception instanceof ConnectException) {
			//java.net.ConnectException: Connection refused
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
		return "JavaHttpSender [" + config.getHostUrl() + "]";
	}
}
