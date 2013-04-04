package com.anthavio.hatatitla.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson 2 com.fasterxml.jackson
 * 
 * @author martin.vanek
 *
 */
public class Jackson2RequestMarshaller implements RequestBodyMarshaller {

	private final ObjectMapper objectMapper;

	public Jackson2RequestMarshaller() {
		this.objectMapper = Jackson2Util.build();
	}

	public Jackson2RequestMarshaller(ObjectMapper mapper) {
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
		return "Jackson2RequestMarshaller [objectMapper=" + objectMapper + "]";
	}

}
