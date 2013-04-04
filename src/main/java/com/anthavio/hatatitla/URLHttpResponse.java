package com.anthavio.hatatitla;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class URLHttpResponse extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpURLConnection connection;

	public URLHttpResponse(int code, String message, Multival headers, InputStream stream, HttpURLConnection connection)
			throws IOException {
		super(code, message, headers, stream);
		if (connection == null) {
			throw new IllegalArgumentException("Null connection");
		}
		this.connection = connection;
	}

	@Override
	public void close() {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException iox) {
				//ignore
			}
		}
	}

	/**
	 * @return underlying HttpURLConnection
	 */
	public HttpURLConnection getConnection() {
		return connection;
	}
}
