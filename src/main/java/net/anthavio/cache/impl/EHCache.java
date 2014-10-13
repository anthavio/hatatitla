package net.anthavio.cache.impl;

import java.io.Serializable;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.cache.CacheKeyProvider;
import net.sf.ehcache.Element;

/**
 * 
 * @author martin.vanek
 *
 */
public class EHCache<K, V extends Serializable> extends CacheBase<K, V> {

	private final net.sf.ehcache.Cache ehCache;

	public EHCache(String name, CacheKeyProvider<K> keyProvider, net.sf.ehcache.Cache ehCache) {
		super(name, keyProvider);
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
		ehCache.put(new Element(cacheKey, entry, Boolean.FALSE, 0, (int) entry.getEvictTtl()));
		return true;
	}

	@Override
	protected Boolean doRemove(String cacheKey) {
		return ehCache.remove(cacheKey);
	}

	@Override
	public void removeAll() {
		ehCache.removeAll();
	}

	@Override
	public void close() {
		super.close();
		//ehCache.dispose(); //we do not controll lifecycle of EhCache
	}

}
