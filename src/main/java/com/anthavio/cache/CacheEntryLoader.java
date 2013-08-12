package com.anthavio.cache;

import org.slf4j.Logger;

import com.anthavio.cache.Cache.RefreshMode;

/**
 * 
 * @author martin.vanek
 *
 */
public interface CacheEntryLoader<V> {

	/**
	 * Implementation should handle differently calls for different RefreshMode
	 * RefreshMode.BLOCK
	 * - Exceptions can be thrown as they are passed to the caller
	 * 
	 * RefreshMode.ASYNC and RefreshMode.SCHEDULE
	 * - handle Exceptions as required
	 * - return null or soft expired value
	 * 
	 * @param softExpired is NOT null only for Soft expired cache entry refresh. Allows to return expired value on failure
	 */
	public LoadResult<V> load(CacheRequest<V> request, CacheEntry<V> softExpired);

	/**
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class LoadResult<V> {

		private final V value;

		//true if value should be put into cache
		private final boolean cacheable;

		public LoadResult(V value, boolean cacheable) {
			this.value = value;
			this.cacheable = cacheable;
		}

		public V getValue() {
			return value;
		}

		public boolean isCacheable() {
			return cacheable;
		}

	}

	public static enum ExceptionMode {
		RETHROW, //Throw CacheLoaderException exception
		LOG_STACK, //Exception logged with full stack trace (WARN level)
		LOG_MESSAGE; //Exception logged as single line message (WARN level) 
	}

	/**
	 * Easy to use CacheEntryLoader base implementation. Just provide getLogger() and doLoad() methods... 
	 * 
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static abstract class BaseCacheLoader<V> implements CacheEntryLoader<V> {

		private boolean returnExpiredOnError = true;

		private boolean cacheExpiredOnError = false;

		private ExceptionMode syncExceptionMode = ExceptionMode.RETHROW;

		private ExceptionMode asyncExceptionMode = ExceptionMode.LOG_STACK;

		public BaseCacheLoader() {
			//defaults...
		}

		public BaseCacheLoader(ExceptionMode syncExceptionMode, ExceptionMode asyncExceptionMode) {
			this(true, false, syncExceptionMode, asyncExceptionMode);
		}

		public BaseCacheLoader(boolean returnExpiredOnError, boolean cacheExpiredOnError, ExceptionMode syncExceptionMode,
				ExceptionMode asyncExceptionMode) {
			this.returnExpiredOnError = returnExpiredOnError;
			this.cacheExpiredOnError = cacheExpiredOnError;
			this.syncExceptionMode = syncExceptionMode;
			this.asyncExceptionMode = asyncExceptionMode;
		}

		@Override
		public LoadResult<V> load(CacheRequest<V> request, CacheEntry<V> softExpired) throws CacheLoaderException {
			LoadResult<V> result;
			try {
				V value = doLoad(request, softExpired);
				return new LoadResult<V>(value, true);
			} catch (Exception x) {
				ExceptionMode xMode;
				if (request.getRefreshMode() == RefreshMode.BLOCK || request.getRefreshMode() == RefreshMode.RETURN) {
					xMode = syncExceptionMode;
				} else { //ASYNC & SCHEDULED
					xMode = asyncExceptionMode;
				}

				if (xMode == ExceptionMode.RETHROW) {
					throw new CacheLoaderException(x, request);
				} else {
					log(xMode, x, request);
					if (returnExpiredOnError && softExpired != null) {
						result = new LoadResult<V>(softExpired.getValue(), cacheExpiredOnError);
					} else {
						result = null;
					}
				}

			}
			return result;
		}

		protected void log(ExceptionMode mode, Exception x, CacheRequest<V> request) {
			if (asyncExceptionMode == ExceptionMode.LOG_MESSAGE) {
				getLogger().warn("Request: " + request + " failed: " + x);
			} else if (asyncExceptionMode == ExceptionMode.LOG_STACK) {
				getLogger().warn("Request " + request + " failed", x);
			}

		}

		/**
		 * @return Logger for errors
		 */
		protected abstract Logger getLogger();

		/**
		 * Meant to be implemented by subclass...
		 * 
		 * returned null is treated as regular value and will be cached
		 */
		protected abstract V doLoad(CacheRequest<V> request, CacheEntry<V> softExpired) throws Exception;
	}

	/**
	 * Checked Exception wrapper for ExceptionMode.RETHROW
	 * 
	 * @author martin.vanek
	 *
	 */
	public class CacheLoaderException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final CacheRequest<?> request;

		public CacheLoaderException(Exception x, CacheRequest<?> request) {
			super(x);
			this.request = request;
		}

		public CacheRequest<?> getRequest() {
			return request;
		}

	}
}
