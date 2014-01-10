package net.anthavio.cache;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.Builders.CacheReadyRequestLoaderBuilder;
import net.anthavio.cache.CacheEntryLoader.CacheEntryLoadResult;
import net.anthavio.cache.CacheEntryLoader.CacheLoaderException;
import net.anthavio.httl.util.Cutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base Cache. Allows easier implementation of the Cache interface
 * 
 * @author martin.vanek
 *
 */
public abstract class CacheBase<V> implements Cache<String, V> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final String name;

	private Scheduler<V> scheduler;

	public CacheBase(String name) {
		this(name, null);
	}

	public CacheBase(String name, ExecutorService executor) {
		this.name = name;
		if (executor != null) {
			this.scheduler = new Scheduler<V>(this, executor);
		}
	}

	public String getName() {
		return this.name;
	}

	/**
	 * Close undelying scheduler (if created)
	 */
	@Override
	public void close() {
		logger.info("Cache close " + getName());
		if (scheduler != null) {
			scheduler.close();
		}
	}

	/**
	 * @return builder to create complex CacheRequest
	 */
	public CacheReadyRequestLoaderBuilder<V> with(CacheEntryLoader<V> key) {
		return new CacheReadyRequestLoaderBuilder<V>(this, key);
	}

	@Override
	public final CacheEntry<V> get(String userKey) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getCacheKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache get: " + userKey + " (" + cacheKey + ")");
		}

		CacheEntry<V> entry;
		try {
			entry = doGet(cacheKey);
		} catch (Exception x) {
			logger.warn("Failed to get value for " + userKey + " (" + cacheKey + ")", x);
			return null;
		}

		if (entry != null && entry.isEvicted()) {
			logger.warn("Cache returned hard expired entry: " + entry + " for " + userKey + " (" + cacheKey + ")");
			//XXX maybe throw away hard expired entry and return null
		}
		if (logger.isDebugEnabled()) {
			if (entry != null) {
				logger.debug("Cache hit: " + userKey + " (" + cacheKey + ")" + " entry: " + entry);
			} else {
				logger.debug("Cache mis: " + userKey + " (" + cacheKey + ")");
			}
		}
		return entry;
	}

	protected abstract CacheEntry<V> doGet(String cacheKey) throws Exception;

	@Override
	public Boolean set(String userKey, V data, long evictTtl, TimeUnit unit) {
		long ttlSeconds = unit.toSeconds(evictTtl);
		CacheEntry<V> entry = new CacheEntry<V>(data, ttlSeconds, ttlSeconds);
		return set(userKey, entry);
	}

	@Override
	public final Boolean set(String userKey, CacheEntry<V> entry) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getCacheKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache set: " + userKey + " (" + cacheKey + ")");
		}
		if (entry.getEvictTtl() < 1) {
			throw new IllegalArgumentException("Evict TTL " + entry.getEvictTtl() + "is < 1");
		}
		try {
			entry.setCached(new Date());
			return doSet(cacheKey, entry);
		} catch (Exception x) {
			entry.setCached(null);
			logger.warn("Failed to set: " + userKey + " (" + cacheKey + ")", x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doSet(String cacheKey, CacheEntry<V> entry) throws Exception;

	@Override
	public final Boolean remove(String userKey) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Key must not be blank");
		}
		String cacheKey = getCacheKey(userKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Cache rem: " + userKey + " (" + cacheKey + ")");
		}
		try {
			return doRemove(cacheKey);
		} catch (Exception x) {
			logger.warn("Failed to remove: " + userKey + " (" + cacheKey + ")", x);
			return Boolean.FALSE;
		}
	}

	protected abstract Boolean doRemove(String cacheKey) throws Exception;

	/**
	 * Client Cache get 
	 */
	public CacheEntry<V> get(CacheLoadRequest<V> request) {
		if ((request.isMissingLoadAsync() || request.isExpiredLoadAsync()) && scheduler == null) {
			throw new IllegalStateException("Scheduler must be configured to execute asynchronous requests");
		}
		String userKey = request.getUserKey();
		CacheEntry<V> entry = get(userKey);
		if (entry != null) {
			if (!entry.isExpired()) {
				return entry; //fresh hit
			} else {
				return load(request, entry);
			}
		} else { //cache miss - we have nothing
			return load(request, entry);
		}
	}

	/**
	 * Client enforced Cache load
	 */
	public CacheEntry<V> load(CacheLoadRequest<V> request, CacheEntry<V> expiredEntry) {
		if (expiredEntry != null) {
			//soft expired - refresh needed
			if (request.isExpiredLoadAsync()) {
				scheduler.startReload(request, expiredEntry); //start asynchronous refresh
				//logger.debug("Soft expired value returned: " + userKey);
				return expiredEntry;
			} else {
				//logger.debug("Sync refresh start " + cacheKey);
				return load(false, request, expiredEntry);
			}
		} else { //cache miss - we have nothing
			if (request.isMissingLoadAsync()) {
				scheduler.startReload(request, null);
				return CacheEntry.EMPTY;
			} else {
				return load(false, request, null);
			}
		}
	}

	/**
	 * Load and store value into cache.
	 */
	protected CacheEntry<V> load(boolean async, CacheLoadRequest<V> request, CacheEntry<V> expiredEntry) {
		CacheEntryLoadResult<V> entry;
		String userKey = request.getUserKey();
		try {
			entry = request.getLoader().load(request, async, expiredEntry);
			if (entry.getCacheSet()) {
				set(request.getUserKey(), entry);
			}
			if (request instanceof ScheduledRequest) {
				((ScheduledRequest<V>) request).setLastRefresh(System.currentTimeMillis());
			}
		} catch (Exception exception) {
			//No exceptions should be here
			if (async) {
				logger.error("Reload failed for key: " + userKey + " request: " + request);
				entry = null;
			} else {
				if (exception instanceof CacheLoaderException) {
					throw (CacheLoaderException) exception;
				} else {
					throw new CacheLoaderException(exception, request);
				}
			}

		}
		return entry;
	}

	/**
	 * Schedule this CacheRequest to be updated(refreshed) automatically in the background.
	 * 
	 * Also starts scheduler thread if it is not running yet.
	 */
	public void schedule(CacheLoadRequest request) {
		if (this.scheduler == null) {
			throw new IllegalStateException("Scheduler for asynchronous refresh is not configured");
		}
		scheduler.schedule(request);
	}

	public Scheduler<V> getScheduler() {
		return scheduler;
	}

	public void setScheduler(Scheduler<V> scheduler) {
		this.scheduler = scheduler;
	}

}
