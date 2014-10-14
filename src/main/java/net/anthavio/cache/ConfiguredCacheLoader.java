package net.anthavio.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class ConfiguredCacheLoader<K, V> extends CacheEntryLoader<K, V> {

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

	/**
	 * Recipe what to do on exception when loading missing cache value (cached value NOT available) 
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class MissingFailedRecipe {

		/**
		 * Preconfigured: dont log & throw exception
		 */
		public static final MissingFailedRecipe SYNC_STRICT = new MissingFailedRecipe(LogErrorAs.NOTHING,
				MissingReturn.EXCEPTION, CacheReturned.DONT);

		/**
		 * Preconfigured: log stacktrace & return null & dont cache
		 */
		public static final MissingFailedRecipe ASYN_STRICT = new MissingFailedRecipe(LogErrorAs.STACKTRACE,
				MissingReturn.NULL, CacheReturned.DONT);

		/**
		 * Preconfigured: log mesage & return null & dont cache
		 */
		public static final MissingFailedRecipe SYNC_NULL = new MissingFailedRecipe(LogErrorAs.MESSAGE, MissingReturn.NULL,
				CacheReturned.DONT);

		private final LogErrorAs logAs;

		private final MissingReturn returnAs;

		private final CacheReturned cacheAs;

		public MissingFailedRecipe(LogErrorAs logAs, MissingReturn returnAs, CacheReturned cacheAs) {
			if (logAs == null) {
				throw new IllegalArgumentException("Null LogErrorAs");
			}
			this.logAs = logAs;

			if (returnAs == null) {
				throw new IllegalArgumentException("Null MissingReturn");
			}
			this.returnAs = returnAs;

			if (cacheAs == null) {
				throw new IllegalArgumentException("Null CacheReturned");
			}
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

	/**
	 * Recipe what to do on exception when loading soft expired cache value (cached value IS available)
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class ExpiredFailedRecipe {

		public static final ExpiredFailedRecipe SYNC_STRICT = new ExpiredFailedRecipe(LogErrorAs.NOTHING,
				ExpiredReturn.EXCEPTION, CacheReturned.DONT);

		public static final ExpiredFailedRecipe SYNC_RETURN = new ExpiredFailedRecipe(LogErrorAs.MESSAGE,
				ExpiredReturn.EXPIRED, CacheReturned.DONT);

		public static final ExpiredFailedRecipe ASYN_STRICT = new ExpiredFailedRecipe(LogErrorAs.STACKTRACE,
				ExpiredReturn.EXPIRED, CacheReturned.DONT);

		private final LogErrorAs logErrorAs;

		private final ExpiredReturn returnAs;

		private final CacheReturned cacheAs;

		public ExpiredFailedRecipe(LogErrorAs logOn, ExpiredReturn returnOn, CacheReturned cacheOn) {
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

	protected final MissingFailedRecipe msettings;

	protected final ExpiredFailedRecipe esettings;

	/**
	 * Create with default exception settings 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader) {
		this(loader, MissingFailedRecipe.SYNC_STRICT, ExpiredFailedRecipe.SYNC_STRICT);
	}

	/**
	 * Create with custom exception settings
	 * 
	 */
	public ConfiguredCacheLoader(SimpleLoader<V> loader, MissingFailedRecipe missSettings, ExpiredFailedRecipe expSettings) {
		this.loader = loader;
		this.logger = LoggerFactory.getLogger(loader.getClass());
		this.msettings = missSettings;
		this.esettings = expSettings;
	}

	/**
	 * Executed on cache miss - Expired value does not exist
	 * 
	 * @param request - original request
	 * @param asyn - flag indicating asynchronous execution of this method
	 * @return result to be returned and possibly cached
	 * @throws Exception
	 */
	protected V loadMissing(CacheLoadRequest<K, V> request, boolean asyn) throws Exception {
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
	protected V loadExpired(CacheLoadRequest<K, V> request, boolean asyn, CacheEntry<V> expiredEntry) throws Exception {
		return loader.load();
	}

	@Override
	protected CacheEntryLoadResult<V> load(CacheLoadRequest<K, V> request, boolean async, CacheEntry<V> expiredEntry) {
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

	protected void logException(Exception exception, CacheLoadRequest<K, V> request, boolean async) {
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
	protected CacheEntryLoadResult<V> getMissingResult(Exception exception, MissingFailedRecipe settings,
			CacheLoadRequest<K, V> request) {
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
	protected CacheEntryLoadResult<V> getExpiredResult(Exception exception, ExpiredFailedRecipe settings,
			CacheLoadRequest<K, V> request, CacheEntry<V> expiredEntry) {
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
			entry = new CacheEntryLoadResult<V>(false, value, expiredEntry.getEvictTtl(), expiredEntry.getStaleTtl());
			entry.setStoredAt(expiredEntry.getStoredAt());
			break;
		case EXPIRED:
			entry = new CacheEntryLoadResult<V>(true, value, expiredEntry.getEvictTtl(), -1);
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
