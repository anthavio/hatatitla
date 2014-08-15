package net.anthavio.httl.transport;

import java.io.IOException;
import java.io.InputStream;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;

import org.apache.http.HttpResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Response extends HttlResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpResponse httpResponse; //non serializable

	public HttpClient4Response(HttlRequest request, int code, String message, Multival<String> headers,
			InputStream stream, HttpResponse httpResponse) throws IOException {
		super(request, code, message, headers, stream);
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
