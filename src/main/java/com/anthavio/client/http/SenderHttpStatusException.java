package com.anthavio.client.http;

import java.io.IOException;

/**
 * Exception including response http status code
 * 
 * @author martin.vanek
 *
 */
public class SenderHttpStatusException extends IOException {

	private static final long serialVersionUID = 1L;

	private final int httpStatusCode;

	public SenderHttpStatusException(int httpStatusCode, String message) {
		super(httpStatusCode + " " + message);
		this.httpStatusCode = httpStatusCode;
	}

	public SenderHttpStatusException(SenderResponse response) throws IOException {
		this(response.getHttpStatusCode(), HttpHeaderUtil.readAsString(response));
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

}
