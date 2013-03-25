package com.anthavio.client.http.async;

import java.io.IOException;

import com.anthavio.client.http.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseHandler {

	public void onResponse(SenderResponse response) throws IOException;

	public void onError(Exception x);
}
