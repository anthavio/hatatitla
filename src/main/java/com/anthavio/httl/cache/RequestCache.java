package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is actualy caching Responses, but SenderRequest is generic parameter here...
 * 
 * @author martin.vanek
 *
 */
public abstract class RequestCache<V> implements Cache<String, V> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final String name;

	public RequestCache(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public final CacheEntry<V> get(String key) {
		//if (logger.isDebugEnabled()) {
		//	logger.debug("Cache get: " + key);
		//}

		CacheEntry<V> entry;
		try {
			entry = doGet(key);
		} catch (Exception x) {
			logger.warn("Failed to get value for: " + key, x);
			return null;
		}

		if (entry != null && entry.isHardExpired()) {
			logger.warn("Cache returned hard expired entry: " + entry + " for: " + key);
			//XXX maybe throw away hard expired entry and return null
		}
		if (logger.isDebugEnabled()) {
			if (entry != null) {
				logger.debug("Cache hit: " + key + " entry: " + entry);
			} else {
				logger.debug("Cache mis: " + key);
			}
		}
		return entry;
	}

	protected abstract CacheEntry<V> doGet(String key) throws Exception;

	@Override
	public Boolean set(String request, V data, long duration, TimeUnit unit) {
		long ttlSeconds = unit.toSeconds(duration);
		CacheEntry<V> entry = new CacheEntry<V>(data, ttlSeconds, ttlSeconds);
		return set(request, entry);
	}

	@Override
	public final Boolean set(String key, CacheEntry<V> entry) {
		if (logger.isDebugEnabled()) {
			logger.debug("Cache set: " + key + " entry: " + entry);
		}
		try {
			return doSet(key, entry);
		} catch (Exception x) {
			logger.warn("Failed to set: " + key, x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doSet(String cacheKey, CacheEntry<V> entry) throws Exception;

	@Override
	public final Boolean remove(String key) {
		if (logger.isDebugEnabled()) {
			logger.debug("Cache rem: " + key);
		}
		try {
			return doRemove(key);
		} catch (Exception x) {
			logger.warn("Failed to remove: " + key, x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doRemove(String cacheKey) throws Exception;

}
