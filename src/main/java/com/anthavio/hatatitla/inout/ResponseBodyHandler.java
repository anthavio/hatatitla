package com.anthavio.hatatitla.inout;

import java.io.IOException;

import com.anthavio.hatatitla.SenderRequest;
import com.anthavio.hatatitla.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseBodyHandler<T> {

	public void onResponse(SenderResponse response, T body) throws IOException;

	public void onRequestError(SenderRequest request, Exception exception);

	public void onResponseError(SenderResponse response, Exception exception);
}
