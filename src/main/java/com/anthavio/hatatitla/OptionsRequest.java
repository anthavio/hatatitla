package com.anthavio.hatatitla;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class OptionsRequest extends SenderRequest {

	private static final Method method = Method.OPTIONS;

	//Constructors of managed request instance knowing it's Sender

	protected OptionsRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected OptionsRequest(HttpSender sender, String urlPath, Multival parameters, Multival headers) {
		super(sender, method, urlPath, parameters, headers);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public OptionsRequest(String urlPath) {
		super(null, method, urlPath);
	}

}
