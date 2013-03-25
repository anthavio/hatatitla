package com.anthavio.client.http;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class GetRequest extends SenderRequest {

	private static final Method method = Method.GET;

	//Constructors of managed request instance knowing it's Sender

	protected GetRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected GetRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public GetRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
