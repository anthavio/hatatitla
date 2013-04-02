package com.anthavio.hatatitla;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class HeadRequest extends SenderRequest {

	private static final Method method = Method.HEAD;

	//Constructors of managed request instance knowing it's Sender

	protected HeadRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected HeadRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public HeadRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
