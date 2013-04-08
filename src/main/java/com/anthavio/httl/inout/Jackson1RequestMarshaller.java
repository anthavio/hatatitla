package com.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Jackson 1.9 org.codehaus.jackson 
 * 
 * @author martin.vanek
 *
 */
public class Jackson1RequestMarshaller implements RequestBodyMarshaller {

	private final ObjectMapper objectMapper;

	public Jackson1RequestMarshaller() {
		this.objectMapper = Jackson1Util.build();
	}

	public Jackson1RequestMarshaller(ObjectMapper mapper) {
		if (mapper == null) {
			throw new IllegalArgumentException("null Jackson mapper");
		}
		this.objectMapper = mapper;
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	@Override
	public String marshall(Object requestBody) throws IOException, JsonGenerationException, JsonMappingException {
		StringWriter swriter = new StringWriter();
		objectMapper.writeValue(swriter, requestBody);
		return swriter.toString();
	}

	@Override
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
		//seems to be impossible to instruct Jacksont to use another character encoding
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		objectMapper.writeValue(writer, requestBody);
	}

	@Override
	public String toString() {
		return "Jackson1RequestMarshaller [objectMapper=" + objectMapper + "]";
	}

}
