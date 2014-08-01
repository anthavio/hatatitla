package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson 2 com.fasterxml.jackson
 * 
 * @author martin.vanek
 *
 */
public class Jackson2Marshaller implements HttlMarshaller {

	private final ObjectMapper objectMapper;

	public Jackson2Marshaller() {
		this.objectMapper = Jackson2Util.build();
	}

	public Jackson2Marshaller(ObjectMapper mapper) {
		if (mapper == null) {
			throw new IllegalArgumentException("null Jackson mapper");
		}
		this.objectMapper = mapper;
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	@Override
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
		//seems to be impossible to instruct Jackson to use another character encoding
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		objectMapper.writeValue(writer, requestBody);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [objectMapper=" + objectMapper + "]";
	}

}
