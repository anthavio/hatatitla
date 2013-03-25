package com.anthavio.hatatitla.async;

import java.io.IOException;

import com.anthavio.hatatitla.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseHandler {

	public void onResponse(SenderResponse response) throws IOException;

	public void onError(Exception x);
}
