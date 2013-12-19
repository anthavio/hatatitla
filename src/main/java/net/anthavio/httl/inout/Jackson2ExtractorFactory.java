package net.anthavio.httl.inout;

import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.SenderResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class Jackson2ExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, Jackson2ResponseExtractor<?>> cache = new HashMap<Class<?>, Jackson2ResponseExtractor<?>>();

	private final ObjectMapper objectMapper;

	/**
	 * Shared Jackson ObjectMapper will be created internally
	 */
	public Jackson2ExtractorFactory() {
		this.objectMapper = Jackson2Util.build();
	}

	/**
	 * Hackis access to internal cache
	 */
	public Map<Class<?>, Jackson2ResponseExtractor<?>> getCache() {
		return cache;
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	/**
	 * External Jackson ObjectMapper is provided
	 */
	public Jackson2ExtractorFactory(ObjectMapper objectMapper) {
		if (objectMapper == null) {
			throw new IllegalArgumentException("Jackson mapper is null");
		}
		this.objectMapper = objectMapper;
	}

	/**
	 * Shared ObjectMapper is used to extract 
	 */
	@Override
	public <T> Jackson2ResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
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
