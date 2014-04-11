package net.anthavio.httl.util;

import net.anthavio.httl.HttpSenderConfig;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockConfig extends HttpSenderConfig {

	public MockConfig() {
		this("http://never.really.sent.anywhere:6363/");
	}

	public MockConfig(String urlString) {
		super(urlString);
	}

	@Override
	public MockSender buildSender() {
		return new MockSender();
	}
}
