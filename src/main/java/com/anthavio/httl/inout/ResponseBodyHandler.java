package com.anthavio.httl.inout;

import java.io.IOException;

import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;

/**
 * Suitable for streaming processing of the Responses 
 * 
 * @author martin.vanek
 *
 */
public interface ResponseBodyHandler<T> {

	/**
	 * Invoked when response is opened. Method is not required to close response.
	 */
	public void onResponse(SenderResponse response, T body) throws IOException;

	/**
	 * Invoked when exception occurs during request sending
	 */
	public void onRequestError(SenderRequest request, Exception exception);

	/**
	 * Invoked when exception occurs during response processing
	 */
	public void onResponseError(SenderResponse response, Exception exception);
}
