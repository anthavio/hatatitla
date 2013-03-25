package com.anthavio.client.http.inout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class Jackson2ExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, Jackson2ResponseExtractor<?>> cache = new HashMap<Class<?>, Jackson2ResponseExtractor<?>>();

	private final ObjectMapper objectMapper;

	public Jackson2ExtractorFactory() {
		this.objectMapper = new ObjectMapper();
	}

	public Map<Class<?>, Jackson2ResponseExtractor<?>> getCache() {
		return cache;
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public Jackson2ExtractorFactory(ObjectMapper objectMapper) {
		if (objectMapper == null) {
			throw new IllegalArgumentException("Jackson mapper is null");
		}
		this.objectMapper = objectMapper;
	}

	@Override
	public <T extends Serializable> Jackson2ResponseExtractor<T> getExtractor(Class<T> resultType) {
		Jackson2ResponseExtractor<T> extractor = (Jackson2ResponseExtractor<T>) cache.get(resultType);
		if (extractor == null) {
			extractor = new Jackson2ResponseExtractor<T>(resultType, objectMapper);
			cache.put(resultType, extractor);
		}
		return extractor;
	}

	@Override
	public String toString() {
		return "Jackson2ExtractorFactory [objectMapper=" + objectMapper + ", cache=" + cache.size() + "]";
	}
}
