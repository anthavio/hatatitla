package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.httl.util.Cutils;

/**
 * Abstract base Cache. Allows easier implementation of the Cache interface
 * 
 * @author martin.vanek
 *
 */
public abstract class CacheBase<V> implements Cache<String, V> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final String name;

	public CacheBase(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public final CacheEntry<V> get(String userKey) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache get: " + userKey + " (" + cacheKey + ")");
		}

		CacheEntry<V> entry;
		try {
			entry = doGet(cacheKey);
		} catch (Exception x) {
			logger.warn("Failed to get value for " + userKey + " (" + cacheKey + ")", x);
			return null;
		}

		if (entry != null && entry.isHardExpired()) {
			logger.warn("Cache returned hard expired entry: " + entry + " for " + userKey + " (" + cacheKey + ")");
			//XXX maybe throw away hard expired entry and return null
		}
		if (logger.isDebugEnabled()) {
			if (entry != null) {
				logger.debug("Cache hit: " + userKey + " (" + cacheKey + ")" + " entry: " + entry);
			} else {
				logger.debug("Cache mis: " + userKey + " (" + cacheKey + ")");
			}
		}
		return entry;
	}

	protected abstract CacheEntry<V> doGet(String cacheKey) throws Exception;

	@Override
	public Boolean set(String userKey, V data, long duration, TimeUnit unit) {
		long ttlSeconds = unit.toSeconds(duration);
		CacheEntry<V> entry = new CacheEntry<V>(data, ttlSeconds, ttlSeconds);
		return set(userKey, entry);
	}

	@Override
	public final Boolean set(String userKey, CacheEntry<V> entry) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache set: " + userKey + " (" + cacheKey + ")");
		}
		try {
			return doSet(cacheKey, entry);
		} catch (Exception x) {
			logger.warn("Failed to set: " + userKey + " (" + cacheKey + ")", x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doSet(String cacheKey, CacheEntry<V> entry) throws Exception;

	@Override
	public final Boolean remove(String userKey) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache rem: " + userKey + " (" + cacheKey + ")");
		}
		try {
			return doRemove(cacheKey);
		} catch (Exception x) {
			logger.warn("Failed to remove: " + userKey + " (" + cacheKey + ")", x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doRemove(String cacheKey) throws Exception;

}
