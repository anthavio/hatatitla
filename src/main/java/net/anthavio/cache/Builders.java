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
		public S cacheFor(long hardTtl, long softTtl, TimeUnit unit) {
			if (softTtl > hardTtl) {
				throw new IllegalArgumentException("Hard TTL must be greater then Soft TTL");
			}
			softTtl(softTtl, unit);
			hardTtl(hardTtl, unit);
			return getSelf();
		}

		/**
		 * Sets hard TTL - How long to keep resource in cache
		 */
		public S hardTtl(long hardTtl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.hardTtl = unit.toSeconds(hardTtl);

			if (this.hardTtl <= 1) {
				throw new IllegalArgumentException("Hard TTL must be at least 1 second");
			}

			if (this.softTtl == 0) {//if unset yet
				this.softTtl = this.hardTtl;
			}

			return getSelf();
		}

		/**
		 * Sets soft TTL - Interval between resource freshness checks
		 */
		public S softTtl(long softTtl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.softTtl = unit.toSeconds(softTtl);

			if (this.softTtl < 1) {
				throw new IllegalArgumentException("Soft TTL must be at least 1 second");
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
	public static class CacheRequestLoaderBuilder<V> extends BaseCacheRequestBuilder<CacheRequestLoaderBuilder<V>> {

		private CacheEntryLoader<V> loader;

		private boolean missingLoadAsync = false;

		private boolean expiredLoadAsync = false;

		public CacheRequestLoaderBuilder(CacheEntryLoader<V> loader) {
			if (loader == null) {
				throw new IllegalArgumentException("Null loader");
			}
			this.loader = loader;
		}

		public CacheRequestLoaderBuilder<V> async(boolean onMissing, boolean onExpired) {
			this.missingLoadAsync = onMissing;
			this.expiredLoadAsync = onExpired;
			return getSelf();
		}

		protected LoadingSettings<V> buildLoadingSettings() {
			return new LoadingSettings<V>(loader, missingLoadAsync, expiredLoadAsync);
		}

		public CacheLoadRequest<V> build() {
			CachingSettings ci = buildCachingSettings();
			LoadingSettings<V> li = buildLoadingSettings();
			return new CacheLoadRequest<V>(ci, li);
		}

		@Override
		protected CacheRequestLoaderBuilder<V> getSelf() {
			return this;
		}
	}

	/**
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class CacheReadyRequestLoaderBuilder<V> extends CacheRequestLoaderBuilder<V> {

		private final CacheBase<V> cache;

		public CacheReadyRequestLoaderBuilder(CacheBase<V> cacheBase, CacheEntryLoader<V> loader) {
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
		protected CacheReadyRequestLoaderBuilder<V> getSelf() {
			return this;
		}
	}

}
