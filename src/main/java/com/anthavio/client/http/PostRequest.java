package com.anthavio.client.http;

import com.anthavio.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class PostRequest extends SenderBodyRequest {

	private static final Method method = Method.POST;

	//Constructors of managed request instance knowing it's Sender

	protected PostRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected PostRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public PostRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
