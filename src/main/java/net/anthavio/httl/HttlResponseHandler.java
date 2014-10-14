package net.anthavio.httl;

import java.io.IOException;

/**
 * 
 * Suitable for streaming processing of the Responses.
 * 
 * Implementation is not required to close Response.
 * 
 * @author martin.vanek
 *
 */
public interface HttlResponseHandler {

	/**
	 * Invoked when response is opened and response ready to be processed by this method.
	 */
	public void onResponse(HttlResponse response) throws IOException;

	/**
	 * Invoked when exception occurs during Request execution 
	 */
	public void onFailure(HttlRequest request, Exception exception);
}
