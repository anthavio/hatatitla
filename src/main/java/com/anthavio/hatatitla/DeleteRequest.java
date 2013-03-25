package com.anthavio.hatatitla;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class DeleteRequest extends SenderRequest {

	private static final Method method = Method.DELETE;

	//Constructors of managed request instance knowing it's Sender

	protected DeleteRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected DeleteRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public DeleteRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
