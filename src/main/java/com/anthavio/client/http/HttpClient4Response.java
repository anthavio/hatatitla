package com.anthavio.client.http;

import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Response extends SenderResponse {

	private HttpResponse httpResponse;

	public HttpClient4Response(int code, String message, Multival headers, InputStream stream, HttpResponse httpResponse) {
		super(code, message, headers, stream);
		if (httpResponse == null) {
			throw new IllegalArgumentException("Null method");
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
