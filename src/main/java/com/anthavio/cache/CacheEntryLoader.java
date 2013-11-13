package com.anthavio.cache;

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

	/**
	 * Product of CacheEntryLoader load method execution
	 * 
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static class CacheEntryLoadResult<V> extends CacheEntry<V> {

		private static final long serialVersionUID = 1L;

		private final boolean cacheSet;

		public CacheEntryLoadResult(boolean cacheSet, V value, long hardTtl, long softTtl) {
			super(value, hardTtl, softTtl);
			this.cacheSet = cacheSet;
		}

		/*
				public CacheEntryLoadResult(boolean cacheSet, V value, CacheLoadRequest<V> request) {
					this(cacheSet, value, request.getHardTtl(), request.getSoftTtl());
				}

				public CacheEntryLoadResult(boolean cacheSet, V value, CacheEntry<V> expiredEntry) {
					this(cacheSet, value, expiredEntry.getHardTtl(), expiredEntry.getSoftTtl());
				}
		*/
		public boolean getCacheSet() {
			return cacheSet;
		}

		@Override
		public String toString() {
			return super.toString() + " cacheSet=" + cacheSet;
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
