package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStreamReader;

import net.anthavio.httl.SenderResponse;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON -> Java 
 * Jackson 2 based ResponseExtractor
 * 
 * httpSender.extract(new GetRequest("/my_entity.json"), new JsonResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class Jackson2ResponseExtractor<T> extends ResponseBodyExtractor<T> {

	private final JavaType resultType;

	private final ObjectMapper mapper;

	/**
	 * Externaly created ObjectMapper is provided
	 */
	public Jackson2ResponseExtractor(JavaType javaType, ObjectMapper mapper) {
		if (javaType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = javaType;

		if (mapper == null) {
			throw new IllegalArgumentException("mapper is null");
		}
		this.mapper = mapper;

	}

	public ObjectMapper getObjectMapper() {
		return mapper;
	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			object = mapper.reader(resultType).readValue(new InputStreamReader(response.getStream(), response.getCharset()));
			return (T) object;
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + resultType + " value: " + object;
			throw new IllegalArgumentException(message, ccx);
		}
	}

	@Override
	public String toString() {
		return "Jackson2ResponseExtractor [resultType=" + resultType + ", mapper=" + mapper + "]";
	}

}