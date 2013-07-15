package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public interface Cache<K, V> {

	public enum RefreshMode {
		/**
		 * refresh is request initiated and done using synchronously using request thread
		 */
		REQUEST_SYNC,
		/**
		 * refresh is request initiated, but done using the background thread
		 */
		REQUEST_ASYN,
		/**
		 * refresh is scheduled and performed using the background thread
		 */
		SCHEDULED;
	}

	public CacheEntry<V> get(K key);

	public CacheEntry<V> get(CacheRequest<V> request);

	public Boolean set(K key, V value, long duration, TimeUnit unit);

	public Boolean set(K key, CacheEntry<V> entry);

	/**
	 * remove/delete/evict from cache by provided key
	 * 
	 * can return null is status of eviction cannot be determined
	 */
	public Boolean remove(K key);

	public void removeAll();

	public void close();

	/**
	 * Cache implementation can use user provided key 
	 * - add prefix or namespace 
	 * - translate it to something else 
	 * - hash it when it is too long
	 */
	public String getCacheKey(K userKey);

}
