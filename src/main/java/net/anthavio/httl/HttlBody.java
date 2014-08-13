package net.anthavio.httl;

import java.io.InputStream;
import java.io.Reader;

/**
 * This is not nice polymorphic, but every HttlTransport needs different code to handle different body source
 * 
 * @author martin.vanek
 *
 */
public class HttlBody {

	public static HttlBody For(Object payload) {
		if (payload == null) {
			throw new IllegalArgumentException("Null payload");
		} else if (payload instanceof String) {
			return new HttlBody((String) payload);
		} else if (payload instanceof byte[]) {
			return new HttlBody((byte[]) payload);
		} else if (payload instanceof InputStream) {
			return new HttlBody((InputStream) payload, false);
		} else if (payload instanceof Reader) {
			return new HttlBody((Reader) payload, false);
		} else {
			return new HttlBody(payload, true);
		}
	}

	public static enum Type {
		STRING, BYTES, READER, STREAM, MARSHALL;
	}

	private final Object payload;

	private final Type type;

	private final boolean cache;

	public HttlBody(String string) {
		this.payload = string;
		this.type = Type.STRING;
		this.cache = true;
	}

	public HttlBody(byte[] bytes) {
		this.payload = bytes;
		this.type = Type.BYTES;
		this.cache = true;
	}

	public HttlBody(InputStream stream, boolean cache) {
		this.payload = stream;
		this.type = Type.STREAM;
		this.cache = cache;
	}

	public HttlBody(Reader reader, boolean cache) {
		this.payload = reader;
		this.type = Type.READER;
		this.cache = cache;
	}

	/**
	 * Sky is the limit! Just implement Marshaller and you can send even JDBC connections
	 */
	public HttlBody(Object payload, boolean cache) {
		this.payload = payload;
		this.type = Type.MARSHALL;
		this.cache = cache;
	}

	/**
	 * true - marshall payload or read stream into cached byte[]
	 * false - mashall payload or write stream directly into output stream
	 */
	public boolean isCache() {
		return cache;
	}

	public Object getPayload() {
		return payload;
	}

	public Type getType() {
		return type;
	}

}
