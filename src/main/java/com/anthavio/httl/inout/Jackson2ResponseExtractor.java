package com.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStreamReader;

import com.anthavio.httl.SenderResponse;
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
public class Jackson2ResponseExtractor<T> implements ResponseBodyExtractor<T> {

	private final Class<T> resultType;

	private final ObjectMapper mapper;

	/**
	 * Externaly created ObjectMapper is provided
	 */
	public Jackson2ResponseExtractor(Class<T> resultType, ObjectMapper mapper) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		if (mapper == null) {
			throw new IllegalArgumentException("mapper is null");
		}
		this.mapper = mapper;

	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			object = mapper.reader(resultType).readValue(new InputStreamReader(response.getStream(), response.getCharset()));
			return (T) object;
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + resultType.getName() + " value: "
					+ object;
			throw new IllegalArgumentException(message, ccx);
		}
	}

	@Override
	public String toString() {
		return "Jackson2ResponseExtractor [resultType=" + resultType + ", mapper=" + mapper + "]";
	}

}