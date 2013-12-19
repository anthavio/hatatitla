package net.anthavio.cache;

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
	public static enum LogErrorAs {
		NOTHING, MESSAGE, STACKTRACE;
	}

	/**
	 * What to return on missing entry load error
	 */
	public static enum MissingReturn {
		EXCEPTION, //throw CacheLoaderException 
		NULL; // return null
	}

	/**
	 * What to return on expired entry load error
	 */
	public static enum ExpiredReturn {
		EXCEPTION, //throw CacheLoaderException 
		EXPIRED, // return expired entry
		NULL; // return null
	}

	/**
	 * What to do with returned value
	 */
	public static enum CacheReturned {
		DONT, // do not cache 
		EXPIRED, // cache as soft expired entry
		REQUEST; // cache as fresh entry from request TTLs
	}

	public static class MissingErrorSettings {

		/**
		 * dont log & throw exception
		 */
		public static final MissingErrorSettings SYNC_STRICT = new MissingErrorSettings(LogErrorAs.NOTHING,
				MissingReturn.EXCEPTION, CacheReturned.DONT);

		/**
		 * log stacktrace & return null & dont cache
		 */
		public static final MissingErrorSettings ASYN_STRICT = new MissingErrorSettings(LogErrorAs.STACKTRACE,
				MissingReturn.NULL, CacheReturned.DONT);

		/**
		 * log mesage & return null & dont cache
		 */
		public static final MissingErrorSettings SYNC_NULL = new MissingErrorSettings(LogErrorAs.MESSAGE,
				MissingReturn.NULL, CacheReturned.DONT);

		private final LogErrorAs logAs;

		private final MissingReturn returnAs;

		private final CacheReturned cacheAs;

		public MissingErrorSettings(LogErrorAs logAs, MissingReturn returnAs, CacheReturned cacheAs) {
			this.logAs = logAs;
			this.returnAs = returnAs;
			this.cacheAs = cacheAs;
		}

		public LogErrorAs getLogAs() {
			return logAs;
		}

		public MissingReturn getReturnAs() {
			return returnAs;
		}

		public CacheReturned getCacheAs() {
			return cacheAs;
		}

	}

	public static class ExpiredErrorSettings {

		public static final ExpiredErrorSettings SYNC_STRICT = new ExpiredErrorSettings(LogErrorAs.NOTHING,
				ExpiredReturn.EXCEPTION, CacheReturned.DONT);

		public static final ExpiredErrorSettings SYNC_RETURN = new ExpiredErrorSettings(LogErrorAs.MESSAGE,
				ExpiredReturn.EXPIRED, CacheReturned.DONT);

		public static final ExpiredErrorSettings ASYN_STRICT = new ExpiredErrorSettings(LogErrorAs.STACKTRACE,
				ExpiredReturn.EXPIRED, CacheReturned.DONT);

		private final LogErrorAs logErrorAs;

		private final ExpiredReturn returnAs;

		private final CacheReturned cacheAs;

		public ExpiredErrorSettings(LogErrorAs logOn, ExpiredReturn returnOn, CacheReturned cacheOn) {
			this.logErrorAs = logOn;
			this.returnAs = returnOn;
			this.cacheAs = cacheOn;
		}

		public LogErrorAs getLogErrorAs() {
			return logErrorAs;
		}

		public ExpiredReturn getReturnAs() {
			return returnAs;
		}

		public CacheReturned getCacheAs() {
			return cacheAs;
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

	protected final MissingErrorSettings msettings;

	protected final ExpiredErrorSettings esettings;

	//protected final MissingExceptionSettings asynMissing;

	//protected final ExpiredExceptionSettings asynExpired;

	/**
	 * Create with default exception settings 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader) {
		this(loader, MissingErrorSettings.SYNC_STRICT, ExpiredErrorSettings.SYNC_STRICT);
	}

	/**
	 * Create with custom exception settings
	 * 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader, MissingErrorSettings msettings, ExpiredErrorSettings esettings) {
		this.loader = loader;
		this.logger = LoggerFactory.getLogger(loader.getClass());
		this.msettings = msettings;
		this.esettings = esettings;
		//this.asynMissing = asynMissing;
		//this.asynExpired = asynExpired;
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
			return new CacheEntryLoadResult<V>(true, value, request.getHardTtl(), request.getSoftTtl());
		} catch (Exception x) {
			if (expiredEntry == null) {
				//MissingErrorSettings msettings = async ? asynMissing : syncMissing;
				logException(x, request, async);
				return getMissingResult(x, msettings, request);
			} else {
				//ExpiredErrorSettings esettings = async ? asynExpired : syncExpired;
				logException(x, request, async);
				return getExpiredResult(x, esettings, request, expiredEntry);
			}
		}
	}

	protected void logException(Exception exception, CacheLoadRequest<V> request, boolean async) {
		switch (msettings.logAs) {
		case NOTHING:
			break;
		case MESSAGE:
			logger.warn("Load failed " + request + " " + exception);
			break;
		case STACKTRACE:
			logger.warn("Load failed " + request, exception);
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + msettings.logAs);
		}

	}

	/**
	 * @param exception
	 * @param settings
	 * @param request
	 * @return
	 */
	protected CacheEntryLoadResult<V> getMissingResult(Exception exception, MissingErrorSettings settings,
			CacheLoadRequest<V> request) {
		V value;
		switch (settings.returnAs) {
		case EXCEPTION:
			throw new CacheLoaderException(exception, request);
		case NULL:
			value = null;
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.returnAs);
		}

		CacheEntryLoadResult<V> entry;
		switch (settings.cacheAs) {
		case DONT:
			entry = new CacheEntryLoadResult<V>(false, value, request.getHardTtl(), request.getSoftTtl());
			break;
		case EXPIRED:
			entry = new CacheEntryLoadResult<V>(true, value, request.getHardTtl(), -1);
			break;
		case REQUEST:
			entry = new CacheEntryLoadResult<V>(true, value, request.getHardTtl(), request.getSoftTtl());
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.cacheAs);
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
	protected CacheEntryLoadResult<V> getExpiredResult(Exception exception, ExpiredErrorSettings settings,
			CacheLoadRequest<V> request, CacheEntry<V> expiredEntry) {
		V value;
		switch (settings.returnAs) {
		case EXCEPTION:
			throw new CacheLoaderException(exception, request);
		case EXPIRED:
			value = expiredEntry.getValue();
			break;
		case NULL:
			value = null;
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.returnAs);
		}

		CacheEntryLoadResult<V> entry;
		switch (settings.cacheAs) {
		case DONT:
			entry = new CacheEntryLoadResult<V>(false, value, expiredEntry.getHardTtl(), expiredEntry.getSoftTtl());
			entry.setCached(expiredEntry.getCached());
			break;
		case EXPIRED:
			entry = new CacheEntryLoadResult<V>(true, value, expiredEntry.getHardTtl(), -1);
			break;
		case REQUEST:
			entry = new CacheEntryLoadResult<V>(true, value, request.getHardTtl(), request.getSoftTtl());
			break;
		default:
			throw new IllegalStateException("Unsupported switch value " + settings.cacheAs);
		}
		return entry;
	}

}
