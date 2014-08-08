package net.anthavio.httl.util;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.SenderBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockSenderConfig extends SenderBuilder {

	private MockTransport transport;

	public MockSenderConfig() {
		this("http://never.sent.anywhere:6363/");
	}

	public MockSenderConfig(MockTransport transport) {
		this();
		transport.setConfig(this);
		this.transport = transport;
	}

	public MockSenderConfig(String urlString) {
		super(urlString);
	}

	@Override
	public HttlSender build() {
		if (transport == null) {
			transport = new MockTransport();
			transport.setConfig(this);
		}
		return new HttlSender(this, transport);
	}

	public MockTransport getTransport() {
		return transport;
	}

}
