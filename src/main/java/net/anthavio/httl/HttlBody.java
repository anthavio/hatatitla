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

	public static enum Type {
		STRING, BYTES, READER, STREAM, MARSHALL;
	}

	private Object payload;

	private Type type;

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
	 * Sky is the limit! Just implement Marshaller and you can send even JDBC connections
	 */
	public HttlBody(Object payload) {
		this.payload = payload;
		this.type = Type.MARSHALL;
	}

	/**
	 * true - marshall payload or read stream into cached byte[]
	 * false - mashall payload or write stream directly into output stream
	public boolean isBuffered() {
		return type == Type.BYTES || type == Type.STRING;
	}
	*/

	public Object getPayload() {
		return payload;
	}

	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return "HttlBody [type=" + type + ", payload=" + payload + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
