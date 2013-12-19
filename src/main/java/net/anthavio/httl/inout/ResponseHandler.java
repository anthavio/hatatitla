package net.anthavio.httl.inout;

import java.io.IOException;

import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;


/**
 * 
 * Suitable for streaming processing of the Responses.
 * 
 * Implementation is not required to close Response.
 * 
 * @author martin.vanek
 *
 */
public interface ResponseHandler {

	/**
	 * Invoked when response is opened and response ready to be processed by this method.
	 */
	public void onResponse(SenderResponse response) throws IOException;

	/**
	 * Invoked when exception occurs during Request sending.
	 *  
	 */
	public void onRequestError(SenderRequest request, Exception exception);

	/**
	 * Invoked when exception occurs during Response processing (probably in onResponse method)
	 */
	public void onResponseError(SenderResponse response, Exception exception);
}
