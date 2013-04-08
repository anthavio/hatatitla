package com.anthavio.httl;

import com.anthavio.httl.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class PutRequest extends SenderBodyRequest {

	private static final Method method = Method.PUT;

	//Constructors of managed request instance knowing it's Sender

	protected PutRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected PutRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public PutRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
