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

	private final Object payload;

	private final Type type;

	private final HttlBodyMarshaller marshaller;

	public HttlBody(String string) {
		this.payload = string;
		this.type = Type.STRING;
		this.marshaller = null;
	}

	public HttlBody(byte[] bytes) {
		this.payload = bytes;
		this.type = Type.BYTES;
		this.marshaller = null;
	}

	public HttlBody(InputStream stream) {
		this.payload = stream;
		this.type = Type.STREAM;
		this.marshaller = null;
	}

	public HttlBody(Reader reader) {
		this.payload = reader;
		this.type = Type.READER;
		this.marshaller = null;
	}

	/**
	 * Sky is the limit! Just implement RequestMarshaller and you can send even JDBC connections
	 */
	public HttlBody(HttlBodyMarshaller marshaller, Object marshallable) {
		if (marshaller == null) {
			throw new HttlRequestException("Null marshaller");
		}
		this.marshaller = marshaller;

		if (marshallable == null) {
			throw new HttlRequestException("Null payload");
		}
		this.payload = marshallable;

		this.type = Type.MARSHALL;
	}

	public Object getPayload() {
		return payload;
	}

	public Type getType() {
		return type;
	}

	public HttlBodyMarshaller getMarshaller() {
		return marshaller;
	}

}
