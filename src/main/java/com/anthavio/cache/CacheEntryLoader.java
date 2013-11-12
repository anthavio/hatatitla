package com.anthavio.cache;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class CacheEntryLoader<V> {

	/**
	 * To be implemented by subclass
	 * 
	 * @param request
	 * @param async - flag of asynchronous execution
	 * @param expiredEntry - null on missing entry load
	 * @return
	 * @throws Exception
	 */
	protected abstract CacheEntryLoadResult<V> load(CacheLoadRequest<V> request, boolean async, CacheEntry<V> expiredEntry)
			throws Exception;

	public static class CacheEntryLoadResult<V> extends CacheEntry<V> {

		private static final long serialVersionUID = 1L;

		private final boolean cached;

		public CacheEntryLoadResult(V value, Date since, long hardTtl, long softTtl, TimeUnit unit, boolean cached) {
			super(value, since, hardTtl, softTtl, unit);
			this.cached = cached;
		}

		public CacheEntryLoadResult(V value, CacheLoadRequest<V> request, boolean cached) {
			this(value, new Date(), request.getHardTtl(), request.getSoftTtl(), TimeUnit.SECONDS, cached);
		}

		public CacheEntryLoadResult(V value, CacheEntry<V> expiredEntry, boolean cached) {
			this(value, expiredEntry.getCreated(), expiredEntry.getHardTtl(), expiredEntry.getSoftTtl(), TimeUnit.SECONDS,
					cached);
		}

		public boolean getCached() {
			return cached;
		}
	}

	/**
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class LoadResult<V> {

		/**
		 * Return null value and also store it in cache 
		 */
		public static final LoadResult<?> NULL_CACHED = new LoadResult(null, true);

		/**
		 * Return null value but do not cache it
		 */
		public static final LoadResult<?> NULL_RETURN = new LoadResult(null, false);

		private final CacheEntry<V> value;

		private final boolean cache; //value is to be cached

		public static <V> LoadResult<V> Cache(CacheEntry<V> value, boolean cached) {
			return new LoadResult<V>(value, cached);
		}

		/**
		 * @param value to be returned
		 * @param cache true - value should be cached, false - value is returned but not cached 
		 */
		public LoadResult(CacheEntry<V> value, boolean cache) {
			this.value = value;
			this.cache = cache;
		}

		public CacheEntry<V> getValue() {
			return value;
		}

		public boolean isCache() {
			return cache;
		}

		@Override
		public String toString() {
			return "LoadResult [value=" + value + ", cached=" + cache + "]";
		}

	}

	public static interface LoadMissingExceptionHandler {

		/**
		 * Callback method - executed when Exception happends when Loading expired cache entry
		 * 
		 * @param exception - To be handled. Thrown from CacheEntryLoader load method
		 * @param async - flag indicating synchronous/asynchronous execution of load method
		 * @param cache - background cache
		 * @throws Exception
		 */
		public void handle(Exception exception, boolean async, CacheBase<?> cache) throws Exception;

	}

	public static interface LoadExpiredExceptionHandler {

		/**
		 * Callback method - executed when Exception happends when Loading expired cache entry
		 * 
		 * @param exception - To be handled. Thrown from CacheEntryLoader load method
		 * @param async - flag indicating synchronous/asynchronous execution of load method
		 * @param cache - background cache
		 * @param expired - previous expired cache entry 
		 * @throws Exception
		 */
		public void handle(Exception exception, boolean async, CacheBase<?> cache, CacheEntry<?> expired) throws Exception;

	}

	public static class DefaultLoadExceptionHandler implements LoadExpiredExceptionHandler, LoadMissingExceptionHandler {

		@Override
		public void handle(Exception exception, boolean async, CacheBase<?> cache, CacheEntry<?> expired) throws Exception {
			if (async) {
				throw exception;
			} else {
				throw exception;
			}
		}

		public void handle(Exception exception, boolean async, CacheBase<?> cache) throws Exception {
			if (async) {
				throw exception;
			} else {
				throw exception;
			}
		}

	}

	/**
	 * Checked Exception wrapper for ExceptionMode.RETHROW
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CacheLoaderException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final CacheLoadRequest<?> request;

		public CacheLoaderException(Exception x, CacheLoadRequest<?> request) {
			super(x);
			this.request = request;
		}

		public CacheLoadRequest<?> getRequest() {
			return request;
		}

	}
}
