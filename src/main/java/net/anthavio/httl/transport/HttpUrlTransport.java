package net.anthavio.httl.transport;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import net.anthavio.httl.Authentication;
import net.anthavio.httl.Authentication.Scheme;
import net.anthavio.httl.HttlBody;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.Base64;
import net.anthavio.httl.util.ReaderInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple java HttpURLConnection implementation of the HttlTransport. No additional library is required
 * 
 * @author martin.vanek
 *
 */
public class HttpUrlTransport implements HttlTransport {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpUrlConfig config;

	private final String basicAuthHeader;

	private HttpURLConnection connection;

	private final SSLSocketFactory sslSocketFactory;

	public HttpUrlTransport(HttpUrlConfig config) {
		this.config = config;

		if (config.getAuthentication() != null) {
			final Authentication authentication = config.getAuthentication();
			if (authentication.getScheme() == Scheme.BASIC && authentication.getPreemptive()) {
				//we can use preemptive shortcut only for basic authentication
				String encoded = Base64.encodeString(authentication.getUsername() + ":" + authentication.getPassword());
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
				this.basicAuthHeader = null;
			}
		} else {
			this.basicAuthHeader = null;
		}
		//Great way to configure stuff...
		//System.setProperty("http.keepAlive", "true");
		//System.setProperty("http.maxConnections", String.valueOf(config.getPoolMaximumSize()));

		if (config.getSslContext() != null && config.getUrl().getProtocol().equals("https")) {
			this.sslSocketFactory = config.getSslContext().getSocketFactory();
		} else {
			this.sslSocketFactory = null; //because final
		}
	}

	@Override
	public HttpUrlConfig getConfig() {
		return config;
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
	public HttlResponse call(HttlRequest request) throws IOException {
		String protocol = config.getUrl().getProtocol();
		URL url = new URL(protocol, config.getUrl().getHost(), config.getUrl().getPort(), request.getPathAndQuery());

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		if (sslSocketFactory != null) {
			((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
		}
		this.connection = connection;

		connection.setUseCaches(false);
		connection.setDoOutput(request.getBody() != null); //connection.getOutputStream() will be called
		connection.setDoInput(true); //connection.getInputStream() will be called

		Multival<String> headers = request.getHeaders();

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

		if (this.basicAuthHeader != null) {
			this.logger.debug("Authorization: " + this.basicAuthHeader);
			connection.setRequestProperty("Authorization", this.basicAuthHeader);
		}

		connection.setRequestMethod(request.getMethod().toString());
		switch (request.getMethod()) {
		case GET:
		case HEAD:
		case OPTIONS:
		case TRACE:
		case DELETE:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", connection.getRequestProperties());
			}
			break;
		case POST:
		case PUT:
		case PATCH:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", connection.getRequestProperties());
			}

			if (request.getBody() != null) {
				HttlBody body = request.getBody();
				switch (body.getType()) {
				case BYTES:
					byte[] abytes = (byte[]) body.getPayload();
					writeBytes(connection, abytes);
					break;
				case STRING:
					String string = (String) body.getPayload();
					byte[] sbytes = string.getBytes(Charset.forName(request.getCharset()));
					writeBytes(connection, sbytes);
					break;
				case MARSHALL:
					request
							.getSender()
							.getMarshaller()
							.marshall(request.getBody().getPayload(), request.getMediaType(), request.getCharset(),
									connection.getOutputStream());
					break;
				case STREAM:
					writeStream(connection, (InputStream) body.getPayload());
					break;
				case READER:
					writeStream(connection, new ReaderInputStream((Reader) body.getPayload()));
					break;
				default:
					throw new IllegalStateException("Unsupported HttlBody type: " + body.getType());
				}
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
			logHeaders("Response", headerFields);
		}
		Multival<String> outHeaders = new Multival<String>();
		for (Entry<String, List<String>> header : headerFields.entrySet()) {
			String hname = header.getKey();
			if (hname != null) { //http status is returned with null header name
				outHeaders.add(hname, header.getValue());
			}
		}

		InputStream responseStream = null;
		try {
			responseStream = connection.getInputStream();
		} catch (IOException iox) {
			return new HttpUrlResponse(request, responseCode, responseMessage, outHeaders, connection.getErrorStream(),
					connection);
		}
		return new HttpUrlResponse(request, responseCode, responseMessage, outHeaders, responseStream, connection);
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
		connection.setRequestProperty(HttlConstants.Content_Length, Integer.toString(dataBytes.length));
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
			ConnectException ctx = new ConnectException("Connection refused " + config.getUrl());
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
		return "JavaUrlSender [" + config.getUrl() + "]";
	}
}
