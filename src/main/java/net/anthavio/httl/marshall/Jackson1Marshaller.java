package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;

import org.codehaus.jackson.map.ObjectMapper;

/**
 * Jackson 1.9 org.codehaus.jackson 
 * 
 * @author martin.vanek
 *
 */
public class Jackson1Marshaller implements HttlBodyMarshaller {

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
	public void marshall(HttlRequest request, OutputStream stream) throws IOException {
		if (!request.getMediaType().contains("json")) {
			throw new HttlRequestException("Cannot mashall into " + request.getMediaType());
		}
		write(request.getBody().getPayload(), stream, request.getCharset());
	}

	public void write(Object requestBody, OutputStream stream, String charset) throws IOException {
		//seems to be impossible to instruct Jackson to use another character encoding
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		objectMapper.writeValue(writer, requestBody);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [objectMapper=" + objectMapper + "]";
	}

}
