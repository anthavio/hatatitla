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
		 * Always fresh data effort mode (or die trying)
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Caller Thread is used to fetch data (blocking) and Fresh value is returned
		 * Exceptions are passed to the caller (no return value)
		 * 
		 * 2. Hit Soft Expired
		 * Caller Thread is used to fetch data (blocking)
		 * Exceptions are logged on WARN level with stacktrace and Soft expired value is returned
		 * This means that caller can only know check that soft expired entry has been returned but exception will be lost to him 
		 * 
		 * 3. Hit Fresh
		 * Return Fresh value
		 */
		BLOCK,

		/**
		 * Always return some data effort mode
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Caller Thread is used to fetch data (blocking) and Fresh value is returned
		 * Exceptions are passed to the caller (no return value)
		 * 
		 * 2. Hit Soft Expired
		 * Soft expired value is returned to the caller
		 * Executor Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace 
		 * 
		 * 3. Hit Fresh
		 * Return Fresh value
		 */
		RETURN,

		/**
		 * Asynchronous mode - returns null
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Null is returned to the caller
		 * Executor Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace
		 * 
		 * 2. Hit Soft Expired
		 * Soft expired value is returned to the caller
		 * Executor Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace
		 * 
		 * 3. Hit Fresh
		 * Return Fresh value
		 */
		ASYNC,

		/**
		 * Scheduled mode - returns null
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Null is returned to the caller
		 * Scheduler Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace 
		 * 
		 * 2. Hit Soft Expired
		 * Soft expired value is returned to the caller
		 * Scheduler Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace
		 * 
		 * 3. Hit Fresh
		 * Return Fresh value
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
