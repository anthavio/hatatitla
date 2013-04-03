package com.anthavio.hatatitla;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpMethodBase;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Response extends SenderResponse {

	private static final long serialVersionUID = 1L;

	private HttpMethodBase httpMethod;

	public HttpClient3Response(int code, String message, Multival headers, InputStream stream, HttpMethodBase httpMethod)
			throws IOException {
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
