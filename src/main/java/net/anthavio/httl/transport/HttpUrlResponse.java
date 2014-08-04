package net.anthavio.httl.transport;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.HttpHeaders;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpUrlResponse extends HttlResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpURLConnection connection;

	public HttpUrlResponse(HttlRequest request, int code, String message, HttpHeaders headers, InputStream stream,
			HttpURLConnection connection) throws IOException {
		super(request, code, message, headers, stream);
		if (connection == null) {
			throw new IllegalArgumentException("Null connection");
		}
		this.connection = connection;
	}

	/*
		@Override
		public void close() {
			Cutils.close(stream);
		}
	*/

	/**
	 * @return underlying HttpURLConnection
	 */
	public HttpURLConnection getConnection() {
		return connection;
	}
}
