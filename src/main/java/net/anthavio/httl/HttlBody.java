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

	private Object payload;

	private Type type;

	private boolean buffered;

	public HttlBody(String string) {
		this.payload = string;
		this.type = Type.STRING;
		this.buffered = true;
	}

	public HttlBody(byte[] bytes) {
		this.payload = bytes;
		this.type = Type.BYTES;
		this.buffered = true;
	}

	public HttlBody(InputStream stream, boolean buffer) {
		this.payload = stream;
		this.type = Type.STREAM;
		this.buffered = buffer;
	}

	public HttlBody(Reader reader, boolean buffer) {
		this.payload = reader;
		this.type = Type.READER;
		this.buffered = buffer;
	}

	/**
	 * Sky is the limit! Just implement Marshaller and you can send even JDBC connections
	 */
	public HttlBody(Object payload, boolean buffer) {
		this.payload = payload;
		this.type = Type.MARSHALL;
		this.buffered = buffer;
	}

	/**
	 * Warning! Mutator!
	 */
	protected void mutate(byte[] bytes) {
		if (type != Type.MARSHALL) {
			throw new IllegalArgumentException("Only MARSHALL Type can be mutated");
		}
		this.type = Type.BYTES;
		this.buffered = true;
		this.payload = bytes;
	}

	/**
	 * true - marshall payload or read stream into cached byte[]
	 * false - mashall payload or write stream directly into output stream
	 */
	public boolean isBuffered() {
		return buffered;
	}

	public Object getPayload() {
		return payload;
	}

	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return "HttlBody [type=" + type + ", buffer=" + buffered + ", payload=" + payload + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (buffered ? 1231 : 1237);
		result = prime * result + ((payload == null) ? 0 : payload.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HttlBody other = (HttlBody) obj;
		if (buffered != other.buffered)
			return false;
		if (payload == null) {
			if (other.payload != null)
				return false;
		} else if (!payload.equals(other.payload))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

}
