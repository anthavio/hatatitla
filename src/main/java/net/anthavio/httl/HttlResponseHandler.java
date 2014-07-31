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
	public void onResponse(HttlRequest request, HttlResponse response) throws IOException;

	/**
	 * Invoked when exception occurs during Response processing (including onResponse method)
	 */
	public void onFailure(HttlRequest request, Exception exception);
}
