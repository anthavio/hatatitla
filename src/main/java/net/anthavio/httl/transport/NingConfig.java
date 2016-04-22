package net.anthavio.httl.transport;

import java.net.URL;

import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class NingConfig extends BaseTransportBuilder<NingConfig> {

	/**
	 * Copy constructor
	 */
	public NingConfig(NingConfig from) {
		super(from);
	}

	public NingConfig(URL url) {
		super(url);
	}

	@Override
	public NingTransport build() {
		return new NingTransport(new NingConfig(this));
	}

	@Override
	public NingConfig getSelf() {
		return this;
	}

}
