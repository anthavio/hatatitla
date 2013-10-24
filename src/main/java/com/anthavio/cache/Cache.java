package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public interface Cache<K, V> {

	public enum RefreshMode { //LoadMode is better name maybe

		/**
		 * Always return fresh data not null
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Caller Thread is used to fetch data (blocking) and Fresh value is returned
		 * Exceptions are passed to the caller wraped into 
		 * 
		 * 2. Soft Expired Hit
		 * Caller Thread is used to fetch data (blocking)
		 * Exceptions are logged on WARN level with stacktrace and Soft expired value is returned
		 * This means that caller can only know check that soft expired entry has been returned but exception will be lost to him
		 *  
		 */
		BLOCK,

		/**
		 * Always return fresh or soft expired data but not null
		 * 
		 * 1. Miss (No Hit / Hard Expired)
		 * Caller Thread is used to fetch data (blocking) and Fresh value is returned
		 * Exceptions are passed to the caller (no return value)
		 * 
		 * 2. Soft Expired Hit
		 * Soft expired value is returned to the caller
		 * Background Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace 
		 * 
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
		 * 2. Soft Expired Hit
		 * Soft expired value is returned to the caller
		 * Background Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace
		 * 
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
		 * 2. Soft Expired Hit
		 * Soft expired value is returned to the caller
		 * Scheduler Thread is used to fetch data
		 * Exceptions are logged on WARN level with stacktrace
		 * 
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
