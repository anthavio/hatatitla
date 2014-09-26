package net.anthavio.httl.util;

import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockTransConfig extends BaseTransBuilder<MockTransConfig> {

	private MockTransport transport;

	public MockTransConfig() {
		super("http://mock.mock.mock:6363/");
	}

	public MockTransConfig(MockTransport transport) {
		this();
		transport.setConfig(this);
		this.transport = transport;
	}

	public MockTransConfig(String url) {
		super(url);
	}

	public MockTransport getTransport() {
		return transport;
	}

	/**
	 * Build MockTransport
	 */
	@Override
	public MockTransport build() {
		if (transport == null) {
			transport = new MockTransport();
			transport.setConfig(this);
		}
		return transport;
	}

	@Override
	public MockTransConfig getSelf() {
		return this;
	}

}
