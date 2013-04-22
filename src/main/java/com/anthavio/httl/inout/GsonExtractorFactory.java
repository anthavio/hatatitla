package com.anthavio.httl.inout;

import java.util.HashMap;
import java.util.Map;

import com.anthavio.httl.SenderResponse;
import com.google.gson.Gson;

/**
 * GSON factory
 * 
 * @author martin.vanek
 *
 */
public class GsonExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, GsonResponseExtractor<?>> cache = new HashMap<Class<?>, GsonResponseExtractor<?>>();

	private final Gson gson;

	/**
	 * Create own shared ObjectMapper
	 */
	public GsonExtractorFactory() {
		this.gson = new Gson();
	}

	/**
	 * @param objectMapper External Jackson ObjectMapper
	 */
	public GsonExtractorFactory(Gson objectMapper) {
		if (objectMapper == null) {
			throw new IllegalArgumentException("objectMapper is null");
		}
		this.gson = objectMapper;
	}

	/**
	 * @return shared gson
	 */
	public Gson getGson() {
		return gson;
	}

	/**
	 * Hackish access to internal cache
	 */
	public Map<Class<?>, GsonResponseExtractor<?>> getCache() {
		return cache;
	}

	@Override
	public <T> GsonResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
		GsonResponseExtractor<T> extractor = (GsonResponseExtractor<T>) cache.get(resultType);
		if (extractor == null) {
			extractor = new GsonResponseExtractor<T>(resultType, gson);
			cache.put(resultType, extractor);
		}
		return extractor;
	}

	@Override
	public String toString() {
		return "GsonExtractorFactory [gson=" + gson + ", cache=" + cache.size() + "]";
	}

}
