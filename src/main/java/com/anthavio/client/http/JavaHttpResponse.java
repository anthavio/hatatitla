package com.anthavio.client.http;

import java.io.InputStream;
import java.net.HttpURLConnection;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class JavaHttpResponse extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpURLConnection connection;

	public JavaHttpResponse(int code, String message, Multival headers, InputStream stream, HttpURLConnection connection) {
		super(code, message, headers, stream);
		if (connection == null) {
			throw new IllegalArgumentException("Null connection");
		}
		this.connection = connection;
	}

	@Override
	public void close() {
		if (connection != null) {
			connection.disconnect();
		}
	}

	/**
	 * @return underlying HttpURLConnection
	 */
	public HttpURLConnection getConnection() {
		return connection;
	}
}
