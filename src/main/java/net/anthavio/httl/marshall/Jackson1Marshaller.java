package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import net.anthavio.httl.HttlMarshaller;

import org.codehaus.jackson.map.ObjectMapper;

/**
 * Jackson 1.9 org.codehaus.jackson 
 * 
 * @author martin.vanek
 *
 */
public class Jackson1Marshaller implements HttlMarshaller {

	private final ObjectMapper objectMapper;

	public Jackson1Marshaller() {
		this.objectMapper = Jackson1Util.build();
	}

	public Jackson1Marshaller(ObjectMapper mapper) {
		if (mapper == null) {
			throw new IllegalArgumentException("Null ObjectMapper");
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
