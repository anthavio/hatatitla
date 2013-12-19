package net.anthavio.httl.inout;

import java.io.IOException;

import net.anthavio.httl.SenderResponse;


/**
 * Sender global error handler. Intended for centralized exception creation
 * 
 * @author martin.vanek
 *
 */
public interface ResponseErrorHandler {

	/**
	 * When http status code >= 300 is found in response, this method is called instead of reponse body extraction
	 */
	public void onErrorResponse(SenderResponse response) throws IOException;

}
