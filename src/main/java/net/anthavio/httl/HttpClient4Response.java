package net.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;

import net.anthavio.httl.HttpSender.Multival;

import org.apache.http.HttpResponse;


/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Response extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpResponse httpResponse; //non serializable

	public HttpClient4Response(int code, String message, Multival headers, InputStream stream, HttpResponse httpResponse)
			throws IOException {
		super(code, message, headers, stream);
		if (httpResponse == null) {
			throw new IllegalArgumentException("Null HttpResponse");
		}
		this.httpResponse = httpResponse;
	}

	/*
		@Override
		public void close() {
			EntityUtils.consumeQuietly(httpResponse.getEntity());
		}
	*/

	/**
	 * Hackish access to HttpResponse
	 */
	public HttpResponse getHttpResponse() {
		return httpResponse;
	}
}
