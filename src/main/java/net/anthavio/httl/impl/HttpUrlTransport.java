package net.anthavio.httl.impl;

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

import net.anthavio.httl.Authentication;
import net.anthavio.httl.Authentication.Scheme;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.SenderBuilder;
import net.anthavio.httl.PseudoStream;
import net.anthavio.httl.inout.RequestMarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.apache.commons.codec.binary.Base64;
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

	private final SenderBuilder config;

	private String basicAuthHeader;

	private HttpURLConnection connection;

	public HttpUrlTransport(String baseUrl) {
		this(new HttpUrlConfig(baseUrl));
	}

	public HttpUrlTransport(HttpUrlConfig config) {
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
		//System.setProperty("http.keepAlive", "true");
		//System.setProperty("http.maxConnections", String.valueOf(config.getPoolMaximumSize()));
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

		URL url = new URL(config.getUrl().getProtocol(), config.getUrl().getHost(), config.getUrl().getPort(),
				request.getPathAndQuery());

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		this.connection = connection;

		connection.setUseCaches(false);
		connection.setDoOutput(request.getBodyStream() != null); //connection.getOutputStream() will be called
		connection.setDoInput(true); //connection.getInputStream() will be called

		HttpHeaders headers = request.getHeaders();

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

			String contentType = request.getFirstHeader(HttlConstants.Content_Type);
			Object[] type = HttpHeaderUtil.splitContentType(contentType, config.getCharset());
			String mimeType = (String) type[0];
			Charset charset = (Charset) type[1];
			if (request.getBodyStream() != null) {
				InputStream stream = request.getBodyStream();
				if (stream instanceof PseudoStream) {
					PseudoStream fake = (PseudoStream) stream;

					if (fake.getValue() instanceof String) {
						String string = (String) fake.getValue();
						byte[] dataBytes = string.getBytes(charset);
						writeBytes(connection, dataBytes);
					} else {
						RequestMarshaller marshaller = config.getRequestMarshaller(mimeType);
						if (marshaller == null) {
							throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
						}
						if (fake.isStreaming()) {
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						} else {
							//XXX create string first an then write...
							marshaller.write(fake.getValue(), connection.getOutputStream(), charset);
						}
					}
				} else {
					writeStream(connection, stream);
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
		HttpHeaders outHeaders = new HttpHeaders();
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
