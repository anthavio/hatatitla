package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Builders.CacheRequestLoaderBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheLoadRequest<V> {

	public static <V> CacheRequestLoaderBuilder<V> With(CacheEntryLoader<V> loader) {
		return new CacheRequestLoaderBuilder<V>(loader);
	}

	protected final CachingSettings caching;

	protected final LoadingSettings<V> loading;

	/**
	 * All field constructor
	 */
	public CacheLoadRequest(String userKey, long hardTtl, long softTtl, TimeUnit unit, CacheEntryLoader<V> loader,
			boolean missingAsyncLoad, boolean expiredAsyncLoad) {
		this.caching = new CachingSettings(userKey, hardTtl, softTtl, unit);
		this.loading = new LoadingSettings<V>(loader, missingAsyncLoad, expiredAsyncLoad);
	}

	public CacheLoadRequest(CachingSettings caching, LoadingSettings<V> loading) {
		if (caching == null) {
			throw new IllegalArgumentException("Null caching settings");
		}
		this.caching = caching;

		if (loading == null) {
			throw new IllegalArgumentException("Null loading settings");
		}
		this.loading = loading;
	}

	public CachingSettings getCaching() {
		return caching;
	}

	public LoadingSettings<V> getLoading() {
		return loading;
	}

	public String getUserKey() {
		return caching.getUserKey();
	}

	public long getHardTtl() {
		return caching.getHardTtl();
	}

	public long getSoftTtl() {
		return caching.getSoftTtl();
	}

	public CacheEntryLoader<V> getLoader() {
		return loading.getLoader();
	}

	public boolean isExpiredLoadAsync() {
		return loading.isExpiredLoadAsync();
	}

	public boolean isMissingLoadAsync() {
		return loading.isMissingLoadAsync();
	}

	/*
		public LoadMissingExceptionHandler getMissingLoadExceptionHandler() {
			return loading.getMissingLoadExceptionHandler();
		}

		public LoadExpiredExceptionHandler getExpiredLoadExceptionHandler() {
			return loading.getExpiredLoadExceptionHandler();
		}
	*/
	@Override
	public String toString() {
		return "CacheRequest key:" + getUserKey();
	}

}
