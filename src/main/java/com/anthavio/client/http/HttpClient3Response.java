package com.anthavio.client.http;

import java.io.InputStream;

import org.apache.commons.httpclient.HttpMethodBase;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Response extends SenderResponse {

	private HttpMethodBase httpMethod;

	public HttpClient3Response(int code, String message, Multival headers, InputStream stream, HttpMethodBase httpMethod) {
		super(code, message, headers, stream);
		if (httpMethod == null) {
			throw new IllegalArgumentException("Null httpMethod");
		}
		this.httpMethod = httpMethod;

	}

	@Override
	public void close() {
		httpMethod.releaseConnection();
	}

	public HttpMethodBase getHttpMethod() {
		return this.httpMethod;
	}

}
