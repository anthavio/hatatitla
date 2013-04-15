package com.anthavio.httl;

import java.io.IOException;

import com.anthavio.httl.util.HttpHeaderUtil;

/**
 * Exception including response http status code
 * 
 * @author martin.vanek
 *
 */
public class SenderHttpStatusException extends SenderException {

	private static final long serialVersionUID = 1L;

	private final int httpStatusCode;

	private final String httpStatusMessage;

	private final String response;

	public SenderHttpStatusException(SenderResponse response) {
		super(response.getHttpStatusCode() + " " + response.getHttpStatusMessage());
		this.httpStatusCode = response.getHttpStatusCode();
		this.httpStatusMessage = response.getHttpStatusMessage();
		try {
			this.response = HttpHeaderUtil.readAsString(response);
		} catch (IOException iox) {
			//XXX maybe just log warning...
			throw new SenderException(iox);
		}
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getHttpStatusMessage() {
		return httpStatusMessage;
	}

	public String getResponse() {
		return response;
	}

}
