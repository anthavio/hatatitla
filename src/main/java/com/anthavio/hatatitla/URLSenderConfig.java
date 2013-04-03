package com.anthavio.hatatitla;

/**
 * 
 * @author martin.vanek
 *
 */
public class URLSenderConfig extends HttpSenderConfig {

	public URLSenderConfig(String urlString) {
		super(urlString);
	}

	@Override
	public URLHttpSender buildSender() {
		return new URLHttpSender(this);
	}

}
