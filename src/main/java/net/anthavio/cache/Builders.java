package net.anthavio.cache;

import java.util.concurrent.TimeUnit;

import net.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public class Builders {

	/**
	 * Most base builder of all builders. Uses generic self trick.
	 * 
	 * @author martin.vanek
	 *
	 * @param <S>
	 */
	public static abstract class BaseCacheRequestBuilder<S extends BaseCacheRequestBuilder<?>> {

		protected String cacheKey;

		protected long softTtl; //seconds

		protected long hardTtl; //seconds

		/**
		 * return generic self 
		 */
		protected abstract S getSelf();

		/**
		 * Sets caching key
		 */
		public S cacheKey(String key) {
			if (Cutils.isBlank(key)) {
				throw new IllegalArgumentException("Blank cache key");
			}
			this.cacheKey = key;
			return getSelf();
		}

		/**
		 * Sets both hard and soft TTL
		 */
		public S cache(long evictTtl, long expiryTtl, TimeUnit unit) {
			if (expiryTtl > evictTtl) {
				throw new IllegalArgumentException("Hard TTL must be greater then Soft TTL");
			}
			expiryTtl(expiryTtl, unit);
			evictTtl(evictTtl, unit);
			return getSelf();
		}

		/**
		 * Sets hard TTL - How long to keep resource in cache
		 */
		public S evictTtl(long evictTtl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.hardTtl = unit.toSeconds(evictTtl);

			if (this.hardTtl < 1) {
				throw new IllegalArgumentException("Evict TTL must be at least 1 second");
			}

			if (this.softTtl == 0) {//if unset yet
				this.softTtl = this.hardTtl;
			}

			return getSelf();
		}

		/**
		 * Sets soft TTL - Interval between resource freshness checks
		 */
		public S expiryTtl(long expiryTtl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.softTtl = unit.toSeconds(expiryTtl);

			if (this.softTtl < 1) {
				throw new IllegalArgumentException("Expiry TTL must be at least 1 second");
			}

			if (this.hardTtl == 0) {
				this.hardTtl = this.softTtl;
			}

			return getSelf();
		}

		protected CachingSettings buildCachingSettings() {
			return new CachingSettings(cacheKey, hardTtl, softTtl, TimeUnit.SECONDS);
		}

		/**
		 * Sets both hard and soft TTL to same value
		public S hardExpire(long ttl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}
			this.hardTtl = unit.toSeconds(ttl);
			if (this.hardTtl <= 1) {
				throw new IllegalArgumentException("TTL must be at least 1 second");
			}
			this.softTtl = hardTtl;

			return getSelf();
		}
		*/
	}

	/**
	 * Concrete builder for CacheRequest<V>
	 * 
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class CacheRequestLoaderBuilder<K, V> extends BaseCacheRequestBuilder<CacheRequestLoaderBuilder<K, V>> {

		private CacheEntryLoader<K, V> loader;

		private boolean missingLoadAsync = false;

		private boolean expiredLoadAsync = false;

		public CacheRequestLoaderBuilder(CacheEntryLoader<K, V> loader) {
			if (loader == null) {
				throw new IllegalArgumentException("Null loader");
			}
			this.loader = loader;
		}

		public CacheRequestLoaderBuilder<K, V> async(boolean onMissing, boolean onExpired) {
			this.missingLoadAsync = onMissing;
			this.expiredLoadAsync = onExpired;
			return getSelf();
		}

		protected LoadingSettings<K, V> buildLoadingSettings() {
			return new LoadingSettings<K, V>(loader, missingLoadAsync, expiredLoadAsync);
		}

		public CacheLoadRequest<K, V> build() {
			CachingSettings ci = buildCachingSettings();
			LoadingSettings<K, V> li = buildLoadingSettings();
			return new CacheLoadRequest<K, V>(ci, li);
		}

		@Override
		protected CacheRequestLoaderBuilder<K, V> getSelf() {
			return this;
		}
	}

	/**
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class CacheReadyRequestLoaderBuilder<K, V> extends CacheRequestLoaderBuilder<K, V> {

		private final CacheBase<K, V> cache;

		public CacheReadyRequestLoaderBuilder(CacheBase<K, V> cacheBase, CacheEntryLoader<K, V> loader) {
			super(loader);
			if (cacheBase == null) {
				throw new IllegalArgumentException("Null cache");
			}
			this.cache = cacheBase;

		}

		public CacheEntry<V> get() {
			return cache.get(build());
		}

		@Override
		protected CacheReadyRequestLoaderBuilder<K, V> getSelf() {
			return this;
		}
	}

}
