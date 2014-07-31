package net.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;

/**
 * XXX This is quite ugly. What about some RequestBodyWriter abstration...
 * 
 * @author martin.vanek
 *
 */
public class PseudoStream extends InputStream {

	private final Object value;

	private final boolean streaming;

	public PseudoStream(Object value) {
		this(value, true);
	}

	public PseudoStream(Object value, boolean streaming) {
		if (value == null) {
			throw new IllegalArgumentException("Null value");
		}
		this.value = value;
		this.streaming = streaming;
	}

	public Object getValue() {
		return value;
	}

	public boolean isStreaming() {
		return streaming;
	}

	@Override
	public void close() throws IOException {
		throw new UnsupportedOperationException("Don't close me!");
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException("Don't read me!");
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + value;
	}
}
