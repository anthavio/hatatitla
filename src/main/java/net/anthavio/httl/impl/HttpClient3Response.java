package net.anthavio.httl.impl;

import java.io.IOException;
import java.io.InputStream;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.HttpHeaders;

import org.apache.commons.httpclient.HttpMethodBase;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Response extends HttlResponse {

	private static final long serialVersionUID = 1L;

	private transient HttpMethodBase httpMethod; //not serializable

	public HttpClient3Response(HttlRequest request, int code, String message, HttpHeaders headers, InputStream stream,
			HttpMethodBase httpMethod) throws IOException {
		super(request, code, message, headers, stream);
		if (httpMethod == null) {
			throw new IllegalArgumentException("Null httpMethod");
		}
		this.httpMethod = httpMethod;

	}

	/*
		@Override
		public void close() {
			//httpMethod.releaseConnection();
		}
	*/

	/**
	 * Hackish access to HttpMethod
	 */
	public HttpMethodBase getHttpMethod() {
		return this.httpMethod;
	}

}
