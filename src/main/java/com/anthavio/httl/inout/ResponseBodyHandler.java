package com.anthavio.httl.inout;

import java.io.IOException;

import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;

/**
 * Suitable for streaming processing of the Responses.
 * 
 * Implementation is not required to close Response.
 * 
 * @author martin.vanek
 *
 */
public interface ResponseBodyHandler<T> {

	/**
	 * Invoked when response is opened and response body extracted.
	 */
	public void onResponse(SenderResponse response, T body) throws IOException;

	/**
	 * Invoked when exception occurs during Request sending.
	 */
	public void onRequestError(SenderRequest request, Exception exception);

	/**
	 * Invoked when exception occurs during Response extraction. 
	 */
	public void onResponseError(SenderResponse response, Exception exception);
}
