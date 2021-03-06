package net.anthavio.cache;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public interface Cache<K, V> {

	public CacheEntry<V> get(K key);

	//public CacheEntry<V> get(CacheRequest<V> request);

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

}
