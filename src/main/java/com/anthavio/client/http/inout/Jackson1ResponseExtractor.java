package com.anthavio.client.http.inout;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import com.anthavio.client.http.SenderResponse;

/**
 *  Json -> Java Jackson library based ResponseExtractor
 * 
 * httpSender.extract(new GetRequest("/my_entity.json"), new JsonResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class Jackson1ResponseExtractor<T> implements ResponseBodyExtractor<T> {

	//primitive cache for Jackson ObjectMappers
	private static Map<Class<?>, ObjectMapper> cache = new HashMap<Class<?>, ObjectMapper>();

	private final Class<T> resultType;

	private final ObjectMapper mapper;

	/**
	 * Creates default Jackson ObjectMapper
	 */
	public Jackson1ResponseExtractor(Class<T> resultType) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		ObjectMapper mapper = cache.get(resultType);
		if (mapper == null) {
			mapper = new ObjectMapper();
			cache.put(resultType, mapper);
		}
		this.mapper = mapper;
	}

	/**
	 * Using custom externaly created ObjectMapper is probably better way...
	 */
	public Jackson1ResponseExtractor(Class<T> resultType, ObjectMapper mapper) {
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
}