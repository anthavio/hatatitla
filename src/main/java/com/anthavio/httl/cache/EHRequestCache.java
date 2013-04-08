package com.anthavio.httl.cache;

import java.io.Serializable;

import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class EHRequestCache<V extends Serializable> extends RequestCache<V> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final net.sf.ehcache.Cache ehCache;

	public EHRequestCache(String name, net.sf.ehcache.Cache ehCache) {
		super(name);
		if (ehCache == null) {
			throw new IllegalArgumentException("null ehCache");
		}
		this.ehCache = ehCache;
		//memoryOnlyCache = new net.sf.ehcache.Cache("testCache", 5000, false, false, 5, 2);
	}

	@Override
	protected CacheEntry<V> doGet(String cacheKey) {
		Element element = ehCache.get(cacheKey);
		if (element != null) {
			return (CacheEntry<V>) element.getObjectValue();
		} else {
			return null;
		}
	}

	@Override
	protected Boolean doSet(String cacheKey, CacheEntry<V> entry) {
		ehCache.put(new Element(cacheKey, entry, Boolean.FALSE, 0, (int) entry.getHardTtl()));
		return true;
	}

	@Override
	public Boolean doRemove(String cacheKey) {
		try {
			return ehCache.remove(cacheKey);
		} catch (Exception x) {
			logger.warn("Failed to remove key " + cacheKey, x);
			return Boolean.FALSE;
		}
	}

	@Override
	public void removeAll() {
		ehCache.removeAll();
	}

	@Override
	public void destroy() {
		ehCache.dispose();
	}

}
