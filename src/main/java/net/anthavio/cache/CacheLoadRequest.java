package net.anthavio.cache;

import java.util.concurrent.TimeUnit;

import net.anthavio.cache.Builders.CacheRequestLoaderBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheLoadRequest<K, V> {

	public static <K, V> CacheRequestLoaderBuilder<K, V> With(CacheEntryLoader<K, V> loader) {
		return new CacheRequestLoaderBuilder<K, V>(loader);
	}

	protected final CachingSettings<K> caching;

	protected final LoadingSettings<K, V> loading;

	/**
	 * All field constructor
	 */
	public CacheLoadRequest(K userKey, long hardTtl, long softTtl, TimeUnit unit, CacheEntryLoader<K, V> loader,
			boolean missingAsyncLoad, boolean expiredAsyncLoad) {
		this.caching = new CachingSettings<K>(userKey, hardTtl, softTtl, unit);
		this.loading = new LoadingSettings<K, V>(loader, missingAsyncLoad, expiredAsyncLoad);
	}

	public CacheLoadRequest(CachingSettings<K> caching, LoadingSettings<K, V> loading) {
		if (caching == null) {
			throw new IllegalArgumentException("Null caching settings");
		}
		this.caching = caching;

		if (loading == null) {
			throw new IllegalArgumentException("Null loading settings");
		}
		this.loading = loading;
	}

	public CachingSettings<K> getCaching() {
		return caching;
	}

	public LoadingSettings<K, V> getLoading() {
		return loading;
	}

	public K getUserKey() {
		return caching.getUserKey();
	}

	public long getHardTtl() {
		return caching.getHardTtl();
	}

	public long getSoftTtl() {
		return caching.getSoftTtl();
	}

	public CacheEntryLoader<K, V> getLoader() {
		return loading.getLoader();
	}

	public boolean isExpiredLoadAsync() {
		return loading.isExpiredLoadAsync();
	}

	public boolean isMissingLoadAsync() {
		return loading.isMissingLoadAsync();
	}

	@Override
	public String toString() {
		return "CacheLoadRequest key=" + getUserKey();
	}

}
