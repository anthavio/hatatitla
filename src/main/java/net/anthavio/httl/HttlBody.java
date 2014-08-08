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
			return new HttlBody((InputStream) payload);
		} else if (payload instanceof Reader) {
			return new HttlBody((Reader) payload);
		} else {
			return new HttlBody(payload);
		}
	}

	public static enum Type {
		STRING, BYTES, READER, STREAM, MARSHALL;
	}

	private final Object payload;

	private final Type type;

	public HttlBody(String string) {
		this.payload = string;
		this.type = Type.STRING;
	}

	public HttlBody(byte[] bytes) {
		this.payload = bytes;
		this.type = Type.BYTES;
	}

	public HttlBody(InputStream stream) {
		this.payload = stream;
		this.type = Type.STREAM;
	}

	public HttlBody(Reader reader) {
		this.payload = reader;
		this.type = Type.READER;
	}

	/**
	 * Sky is the limit! Just implement RequestMarshaller and you can send even JDBC connections
	 */
	public HttlBody(Object payload) {
		this.payload = payload;
		this.type = Type.MARSHALL;
	}

	public Object getPayload() {
		return payload;
	}

	public Type getType() {
		return type;
	}

}
