package com.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import com.anthavio.httl.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Response extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private HttpResponse httpResponse;

	public HttpClient4Response(int code, String message, Multival headers, InputStream stream, HttpResponse httpResponse)
			throws IOException {
		super(code, message, headers, stream);
		if (httpResponse == null) {
			throw new IllegalArgumentException("Null HttpResponse");
		}
		this.httpResponse = httpResponse;
	}

	@Override
	public void close() {
		EntityUtils.consumeQuietly(httpResponse.getEntity());
	}

	public HttpResponse getHttpResponse() {
		return httpResponse;
	}
}
