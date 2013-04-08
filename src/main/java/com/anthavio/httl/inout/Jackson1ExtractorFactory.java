package com.anthavio.httl.inout;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import com.anthavio.httl.SenderResponse;

/**
 * Jackson1 (org.codehaus.jackson) factory
 * 
 * @author martin.vanek
 *
 */
public class Jackson1ExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, Jackson1ResponseExtractor<?>> cache = new HashMap<Class<?>, Jackson1ResponseExtractor<?>>();

	private final ObjectMapper objectMapper;

	public Jackson1ExtractorFactory() {
		this.objectMapper = Jackson1Util.build();
	}

	public Jackson1ExtractorFactory(ObjectMapper objectMapper) {
		if (objectMapper == null) {
			throw new IllegalArgumentException("objectMapper is null");
		}
		this.objectMapper = objectMapper;
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public Map<Class<?>, Jackson1ResponseExtractor<?>> getCache() {
		return cache;
	}

	@Override
	public <T> Jackson1ResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
		Jackson1ResponseExtractor<T> extractor = (Jackson1ResponseExtractor<T>) cache.get(resultType);
		if (extractor == null) {
			extractor = new Jackson1ResponseExtractor<T>(resultType, objectMapper);
			cache.put(resultType, extractor);
		}
		return extractor;
	}

	@Override
	public String toString() {
		return "Jackson1ExtractorFactory [objectMapper=" + objectMapper + ", cache=" + cache.size() + "]";
	}

}
