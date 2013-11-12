package com.anthavio.cache;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class ConfiguredCacheLoader<V> extends CacheEntryLoader<V> {
	/*
		public static class ConfiguredCacheLoaderBuilder<V> {

			private final SimpleLoader<V> loader;

			public ConfiguredCacheLoaderBuilder(SimpleLoader<V> loader) {
				this.loader = loader;
			}
		}

		public ConfiguredCacheLoader<V> onMissing() {
			return this;
		}

		public ConfiguredCacheLoader<V> onExpired() {
			return this;
		}
	*/

	/**
	 * What to log on entry load exception
	 */
	public static enum LogException {
		NOTHING, MESSAGE, STACKTRACE;
	}

	/**
	 * What to return on missing entry load exception
	 */
	public static enum ReturnOnMissing {
		EXCEPTION, //throw CacheLoaderException 
		NULL; // return null
	}

	/**
	 * What to return on expired entry load exception
	 */
	public static enum ReturnOnExpired {
		EXCEPTION, //throw CacheLoaderException 
		EXPIRED, // return expired entry
		NULL; // return null
	}

	/**
	 * What to do with returned value
	 */
	public static enum CacheValue {
		DONT, // do not cache 
		EXPIRED, // cache as expired entry
		FRESH; // cache as fresh entry
	}

	public static class MissingExceptionSettings {

		public static final MissingExceptionSettings SYNC_STRICT = new MissingExceptionSettings(LogException.NOTHING,
				ReturnOnMissing.EXCEPTION, CacheValue.DONT);

		public static final MissingExceptionSettings SYNC_NULL = new MissingExceptionSettings(LogException.MESSAGE,
				ReturnOnMissing.NULL, CacheValue.DONT);

		public static final MissingExceptionSettings ASYN_STRICT = new MissingExceptionSettings(LogException.STACKTRACE,
				ReturnOnMissing.NULL, CacheValue.DONT);

		private final LogException logOn;

		private final ReturnOnMissing returnOn;

		private final CacheValue cacheOn;

		public MissingExceptionSettings(LogException log, ReturnOnMissing returnOn, CacheValue cache) {
			this.logOn = log;
			this.returnOn = returnOn;
			this.cacheOn = cache;
		}

		public LogException getLogOn() {
			return logOn;
		}

		public ReturnOnMissing getReturnOn() {
			return returnOn;
		}

		public CacheValue isCacheOn() {
			return cacheOn;
		}

	}

	public static class ExpiredExceptionSettings {

		public static final ExpiredExceptionSettings SYNC_STRICT = new ExpiredExceptionSettings(LogException.NOTHING,
				ReturnOnExpired.EXCEPTION, CacheValue.DONT);

		public static final ExpiredExceptionSettings SYNC_RETURN = new ExpiredExceptionSettings(LogException.MESSAGE,
				ReturnOnExpired.EXPIRED, CacheValue.DONT);

		public static final ExpiredExceptionSettings ASYN_STRICT = new ExpiredExceptionSettings(LogException.STACKTRACE,
				ReturnOnExpired.EXPIRED, CacheValue.DONT);

		private final LogException logOn;

		private final ReturnOnExpired returnOn;

		private final CacheValue cacheOn;

		public ExpiredExceptionSettings(LogException logOn, ReturnOnExpired returnOn, CacheValue cacheOn) {
			this.logOn = logOn;
			this.returnOn = returnOn;
			this.cacheOn = cacheOn;
		}

		public LogException getLogOn() {
			return logOn;
		}

		public ReturnOnExpired getReturnOn() {
			return returnOn;
		}

		public CacheValue getCacheOn() {
			return cacheOn;
		}

	}

	/**
	 * @author martin.vanek
	 *
	 * @param <V>
	 */
	public static interface SimpleLoader<V> {

		public V load() throws Exception;
	}

	private final Logger logger;

	private final SimpleLoader<V> loader;

	protected final MissingExceptionSettings syncMissing;

	protected final ExpiredExceptionSettings syncExpired;

	protected final MissingExceptionSettings asynMissing;

	protected final ExpiredExceptionSettings asynExpired;

	/**
	 * Create with default exception settings 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader) {
		this(loader, MissingExceptionSettings.SYNC_STRICT, ExpiredExceptionSettings.SYNC_STRICT,
				MissingExceptionSettings.ASYN_STRICT, ExpiredExceptionSettings.ASYN_STRICT);
	}

	/**
	 * Create with custom exception settings
	 * 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader, MissingExceptionSettings syncMissing,
			ExpiredExceptionSettings syncExpired, MissingExceptionSettings asynMissing, ExpiredExceptionSettings asynExpired) {
		this.loader = loader;
		this.logger = LoggerFactory.getLogger(loader.getClass());
		this.syncMissing = syncMissing;
		this.syncExpired = syncExpired;
		this.asynMissing = asynMissing;
		this.asynExpired = asynExpired;
	}

	/**
	 * Executed on cache miss - Expired value does not exist
	 * 
	 * @param request - original request
	 * @param asyn - flag indicating asynchronous execution of this method
	 * @return result to be returned and possibly cached
	 * @throws Exception
	 */
	protected V loadMissing(CacheLoadRequest<V> request, boolean asyn) throws Exception {
		return loader.load();
	}

	/**
	 * Executed on cache expired hit - Expired value exists
	 * 
	 * @param request - original request
	 * @param asyn - flag indicating asynchronous execution of this method
	 * @param expiredEntry - previous expired cache entry with value
	 * @return result to be returned and possibly cached
	 * @throws Exception
	 */
	protected V loadExpired(CacheLoadRequest<V> request, boolean asyn, CacheEntry<V> expiredEntry) throws Exception {
		return loader.load();
	}

	@Override
	protected CacheEntryLoadResult<V> load(CacheLoadRequest<V> request, boolean async, CacheEntry<V> expiredEntry) {
		try {
			//return load();
			V value;
			if (expiredEntry == null) {
				value = loadMissing(request, async);
			} else {
				value = loadExpired(request, async, expiredEntry);
			}
			return new CacheEntryLoadResult<V>(value, request, true);
		} catch (Exception x) {
			if (expiredEntry == null) {
				MissingExceptionSettings msettings = async ? asynMissing : syncMissing;
				logException(x, msettings.logOn, request);
				return getMissingResult(x, msettings, request);
			} else {
				ExpiredExceptionSettings esettings = async ? asynExpired : syncExpired;
				logException(x, esettings.logOn, request);
				return getExpiredResult(x, esettings, request, expiredEntry);
			}
		}
	}

	protected void logException(Exception exception, LogException settings, CacheLoadRequest<V> request) {
		switch (settings) {
		case NOTHING:
			break;
		case MESSAGE:
			logger.warn("Load failed " + request + " " + exception);
			break;
		case STACKTRACE:
			logger.warn("Load failed " + request, exception);
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings);
		}

	}

	/**
	 * @param exception
	 * @param settings
	 * @param request
	 * @return
	 */
	protected CacheEntryLoadResult<V> getMissingResult(Exception exception, MissingExceptionSettings settings,
			CacheLoadRequest<V> request) {
		V value;
		switch (settings.returnOn) {
		case EXCEPTION:
			throw new CacheLoaderException(exception, request);
		case NULL:
			value = null;
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.returnOn);
		}

		CacheEntryLoadResult<V> entry;
		switch (settings.cacheOn) {
		case DONT:
			entry = new CacheEntryLoadResult<V>(value, new Date(), -1, -1, TimeUnit.SECONDS, false);
			break;
		case EXPIRED:
			entry = new CacheEntryLoadResult<V>(value, new Date(), request.getHardTtl(), -1, TimeUnit.SECONDS, true);
			break;
		case FRESH:
			entry = new CacheEntryLoadResult<V>(value, new Date(), request.getHardTtl(), request.getSoftTtl(),
					TimeUnit.SECONDS, true);
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.cacheOn);
		}
		return entry;
	}

	/**
	 * @param exception
	 * @param settings
	 * @param request
	 * @param expiredEntry - previous expired cache entry
	 * @return
	 */
	protected CacheEntryLoadResult<V> getExpiredResult(Exception exception, ExpiredExceptionSettings settings,
			CacheLoadRequest<V> request, CacheEntry<V> expiredEntry) {
		V value;
		switch (settings.returnOn) {
		case EXCEPTION:
			throw new CacheLoaderException(exception, request);
		case EXPIRED:
			value = expiredEntry.getValue();
			break;
		case NULL:
			value = null;
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.returnOn);
		}

		CacheEntryLoadResult<V> entry;
		switch (settings.cacheOn) {
		case DONT:
			entry = new CacheEntryLoadResult<V>(value, expiredEntry, false);
			break;
		case EXPIRED:
			entry = new CacheEntryLoadResult<V>(value, expiredEntry, true);
			break;
		case FRESH:
			entry = new CacheEntryLoadResult<V>(value, request, true);
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.cacheOn);
		}
		return entry;
	}

}
