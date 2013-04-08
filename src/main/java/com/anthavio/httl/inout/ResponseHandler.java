package com.anthavio.httl.inout;

import java.io.IOException;

import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseHandler {

	public void onResponse(SenderResponse response) throws IOException;

	public void onRequestError(SenderRequest request, Exception exception);

	public void onResponseError(SenderResponse response, Exception exception);
}
