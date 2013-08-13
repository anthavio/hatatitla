package com.anthavio.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.cache.CacheEntryLoader.LoadResult;
import com.anthavio.httl.util.Cutils;

/**
 * Abstract base Cache. Allows easier implementation of the Cache interface
 * 
 * @author martin.vanek
 *
 */
public abstract class CacheBase<V> implements Cache<String, V> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final String name;

	private Map<String, CacheRequest<V>> refresh = new HashMap<String, CacheRequest<V>>();

	private ExecutorService executor;

	private Map<String, CacheRequest<V>> scheduled = new HashMap<String, CacheRequest<V>>();

	private long schedulerInterval = 1; //SECONDS

	private RefreshSchedulerThread scheduler;

	public CacheBase(String name) {
		this(name, null);
	}

	public CacheBase(String name, ExecutorService executor) {
		this.name = name;
		this.executor = executor;
	}

	public String getName() {
		return this.name;
	}

	/**
	 * @return requests scheduled to be refreshed automaticaly
	 */
	public Map<String, CacheRequest<V>> getScheduled() {
		return scheduled;
	}

	/**
	 * @return 
	 */
	public CacheRequest<V> getScheduled(String userKey) {
		return scheduled.get(userKey);
	}

	/**
	 * @return scheduler thread sleep time  
	 */
	public long getSchedulerInterval() {
		return schedulerInterval;
	}

	public void setSchedulerInterval(long interval, TimeUnit unit) {
		this.schedulerInterval = unit.toSeconds(interval);
		if (this.schedulerInterval < 1) {
			throw new IllegalArgumentException("Scheduler interval " + schedulerInterval + " must be >= 1 second");
		}
	}

	/**
	 * @return underylying ExecutorService
	 */
	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	/**
	 * Close undelying scheduler (if created)
	 */
	@Override
	public void close() {
		logger.info("Cache close " + getName());
		if (scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}

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

		if (entry != null && entry.isHardExpired()) {
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
	public Boolean set(String userKey, V data, long timeToLive, TimeUnit unit) {
		long ttlSeconds = unit.toSeconds(timeToLive);
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
		try {
			return doSet(cacheKey, entry);
		} catch (Exception x) {
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
	 * Ultimate CacheRequest method 
	 */
	public CacheEntry<V> get(CacheRequest<V> request) {
		String userKey = request.getUserKey();
		CacheEntry<V> entry = get(userKey);
		RefreshMode mode = request.getRefreshMode();
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				if (mode == RefreshMode.SCHEDULED && scheduled.get(userKey) == null) {
					schedule(request); // Entry found in cache but not scheduled (happens when service is restarted and persistent cache is used)
				}
				return entry; //fresh hit
			} else {
				//soft expired - refresh needed
				logger.debug("Soft expired: " + userKey);
				if (mode == RefreshMode.BLOCK) {
					//logger.debug("Sync refresh start " + cacheKey);
					return syncRefresh(request, entry);
				} else if (mode == RefreshMode.ASYNC || mode == RefreshMode.RETURN) {
					asyncRefresh(request, entry); //start asynchronous refresh
					//logger.debug("Soft expired value returned: " + userKey);
					return entry; //return soft expired value
				} else if (mode == RefreshMode.SCHEDULED) {
					if (scheduled.get(userKey) == null) {
						asyncRefresh(request, entry); //start asynchronous refresh
						schedule(request); //register as scheduled
					}
					//logger.debug("Soft expired value returned: " + userKey);
					return entry; //return soft expired value
				} else {
					throw new IllegalArgumentException("Unknown RefreshMode " + mode);
				}
			}
		} else { //cache miss - we have nothing
			if (mode == RefreshMode.BLOCK || mode == RefreshMode.RETURN) {
				return syncRefresh(request, null);
			} else if (mode == RefreshMode.ASYNC) {
				asyncRefresh(request, null);
				return null;
			} else if (mode == RefreshMode.SCHEDULED) {
				if (scheduled.get(userKey) == null) {
					asyncRefresh(request, null);
					schedule(request);
				}
				return null;
			} else {
				throw new IllegalArgumentException("Unknown RefreshMode " + mode);
			}
		}
	}

	private void asyncRefresh(CacheRequest<V> request, CacheEntry<V> softExpiredEntry) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous refresh is not configured");
		}
		String userKey = request.getUserKey();
		synchronized (refresh) {
			if (refresh.containsKey(userKey)) {
				logger.debug("Async refresh already running: " + userKey);
			} else {
				logger.debug("Async refresh starting: " + userKey);
				try {
					executor.execute(new RefreshRunnable<V>(request, softExpiredEntry)); //throws Exception...
				} catch (RejectedExecutionException rx) {
					logger.warn("Async refresh rejected " + rx.getMessage());
				} catch (Exception x) {
					logger.error("Async refresh start failed: " + userKey, x);
				}
			}
		}
	}

	/**
	 * Just do the business. Do NOT handle Exceptions here
	 */
	private CacheEntry<V> syncRefresh(CacheRequest<V> request, CacheEntry<V> softExpiredEntry) {
		CacheEntry<V> entry = null;
		String userKey = request.getUserKey();
		try {
			refresh.put(userKey, request);
			LoadResult<V> result = request.getLoader().load(request, softExpiredEntry);
			if (result != null) {
				request.setLastRefresh(System.currentTimeMillis());
				entry = new CacheEntry<V>(result.getValue(), request.getHardTtl(), request.getSoftTtl());
				if (result.isCacheable()) {
					set(request.getUserKey(), entry);
				}
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("CacheEntryFetch for " + userKey + " returned null");
				}
			}
		} finally {
			refresh.remove(userKey);
		}
		return entry;
	}

	/**
	 * Schedule this CacheRequest to be updated(refreshed) automatically in the background.
	 * Schedule only if NOT already scheduled 
	 * 
	 * Also starts scheduler thread if it is not running yet.
	 */
	public void schedule(CacheRequest<V> request) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous refresh is not configured");
		}
		String userKey = request.getUserKey();
		synchronized (scheduled) {
			if (scheduled.get(userKey) != null) {
				logger.debug("Request already scheduled for refresh: " + userKey);
			} else {
				scheduled.put(userKey, request);
				if (logger.isDebugEnabled()) {
					logger.debug("Request became scheduled for refresh: " + userKey);
				}
				synchronized (this) {
					if (scheduler == null) {
						scheduler = new RefreshSchedulerThread(schedulerInterval, TimeUnit.SECONDS);
						scheduler.start();
					}
				}
			}
		}
	}

	/**
	 * Runnable for {@link RefreshMode#ASYNC}
	 * 
	 */
	private class RefreshRunnable<X> implements Runnable {

		private final CacheRequest<V> request;

		private final CacheEntry<V> softExpiredEntry;

		public RefreshRunnable(CacheRequest<V> request, CacheEntry<V> softExpiredEntry) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
			this.softExpiredEntry = softExpiredEntry;
		}

		@Override
		public void run() {
			try {
				syncRefresh(request, softExpiredEntry);
				if (logger.isDebugEnabled()) {
					logger.debug("Async refresh completed: " + request.getUserKey());
				}
			} catch (Exception x) {
				logger.warn("Failed to fetch " + request, x);
			}
		}
	}

	/**
	 * Thread for {@link RefreshMode#SCHEDULED}
	 * 
	 */
	private class RefreshSchedulerThread extends Thread {

		private final long interval;

		private boolean keepGoing = true;

		public RefreshSchedulerThread(long interval, TimeUnit unit) {
			this.interval = unit.toMillis(interval);
			if (this.interval < 1000) {
				throw new IllegalArgumentException("Interval " + this.interval + " must be >= 1000 millis");
			}
			setDaemon(true);
			setName("scheduler-" + CacheBase.this.getName());
		}

		@Override
		public void run() {
			logger.info(getName() + " started");
			while (keepGoing) {
				try {
					Thread.sleep(interval);
				} catch (InterruptedException ix) {
					logger.info(getName() + " interrupted");
					if (!keepGoing) {
						break;
					}
				}
				try {
					check();
				} catch (Exception x) {
					logger.warn(getName() + " check iteration failed", x);
				}
			}
			logger.info(getName() + " stopped");
		}

		private void check() {
			long now = System.currentTimeMillis();
			if (logger.isTraceEnabled()) {
				logger.trace(getName() + " check");
			}
			for (Entry<String, CacheRequest<V>> entry : scheduled.entrySet()) {
				try {
					CacheRequest request = entry.getValue();
					if (request.getSoftExpire() < now) {
						asyncRefresh(request, null);
					}
				} catch (Exception x) {
					logger.warn("Exception during refresh of " + entry.getValue(), x);
				}
			}
		}
	}

}
