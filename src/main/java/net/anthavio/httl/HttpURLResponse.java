package net.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import net.anthavio.httl.HttpSender.Multival;


/**
 * 
 * @author martin.vanek
 *
 */
public class HttpURLResponse extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpURLConnection connection;

	public HttpURLResponse(int code, String message, Multival headers, InputStream stream, HttpURLConnection connection)
			throws IOException {
		super(code, message, headers, stream);
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
