package net.anthavio.httl.inout;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.SenderResponse;

import com.google.gson.Gson;

/**
 * GSON factory
 * 
 * @author martin.vanek
 *
 */
public class GsonExtractorFactory implements ResponseExtractorFactory {

	private final Map<Type, GsonResponseExtractor<?>> cache = new HashMap<Type, GsonResponseExtractor<?>>();

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
	public Map<Type, GsonResponseExtractor<?>> getCache() {
		return cache;
	}

	@Override
	public <T> GsonResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
		return getExtractor(response, (Type) resultType);
	}

	@Override
	public <T> GsonResponseExtractor<T> getExtractor(SenderResponse response, ParameterizedType resultType) {
		return getExtractor(response, (Type) resultType);
	}

	@Override
	public <T> GsonResponseExtractor<T> getExtractor(SenderResponse response, Type resultType) {
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
