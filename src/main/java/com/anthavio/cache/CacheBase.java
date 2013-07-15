package com.anthavio.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private Map<String, CacheRequest<V>> updated = new HashMap<String, CacheRequest<V>>();

	private ExecutorService executor;

	private Map<String, CacheRequest<V>> scheduled = new HashMap<String, CacheRequest<V>>();

	private RefreshSchedulerThread scheduler;

	public CacheBase(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
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
	 * 
	 * @param request
	 * @return
	 */
	public CacheEntry<V> get(CacheRequest<V> request) {
		if (request.isAsyncRefresh() && this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		String cacheKey = request.getUserKey();
		CacheEntry<V> entry = get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return entry; //nice hit
			} else {
				logger.debug("Request soft expired " + cacheKey);
				//soft expired - refresh needed
				if (request.isAsyncRefresh()) {
					//we will return soft expired value, but we will also start asynchronous refresh
					startRefresh(request);
					if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
						addScheduled(request);
					}
					logger.debug("Request soft expired value returned " + cacheKey);
					return entry;
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						updated.put(cacheKey, request);
						V value = request.getUpdater().fetch(request);
						request.setLastRefresh(System.currentTimeMillis());
						return doCachePut(request, value);
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - but we still have our soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return entry;
					} finally {
						updated.remove(cacheKey);
					}
				}
			}
		} else { //null entry -> execute request, extract response and put it into cache
			V value = request.getUpdater().fetch(request);
			request.setLastRefresh(System.currentTimeMillis());
			if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
				addScheduled(request);
			}
			return doCachePut(request, value);
		}
	}

	private void startRefresh(CacheRequest<V> request) {
		String cacheKey = request.getUserKey();
		synchronized (updated) {
			if (updated.containsKey(cacheKey)) {
				logger.debug("Request is already being refreshed " + cacheKey);
			} else {
				logger.debug("Request async refresh start " + cacheKey);
				executor.execute(new CacheUpdateRunner<V>(request));
				updated.put(cacheKey, request); //only if executor did not refused new task
			}
		}
	}

	/**
	 * Puts Response into Cache
	 * @return 
	 */
	private CacheEntry<V> doCachePut(CacheRequest<V> request, V value) {
		CacheEntry<V> entry = new CacheEntry<V>(value, request.getHardTtl(), request.getSoftTtl());
		set(request.getUserKey(), entry);
		return entry;
	}

	/**
	 * Schedule this CacheRequest to be updated(refreshed) automatically in the background. 
	 * 
	 * Also starts scheduler thread if it is not running yet.
	 */
	private void addScheduled(CacheRequest<V> request) {
		String cacheKey = request.getUserKey();
		//schedule if not already scheduled
		if (scheduled.get(cacheKey) != null) {
			logger.debug("Request is already scheduled to refresh" + cacheKey);
		} else {
			scheduled.put(cacheKey, request);
			logger.debug("Request is now scheduled to be refreshed " + cacheKey);
			synchronized (this) {
				if (scheduler == null) {
					logger.info("RefreshSchedulerThread started");
					scheduler = new RefreshSchedulerThread(1, TimeUnit.SECONDS);
					scheduler.start();
				}
			}
		}
	}

	/**
	 * Runnable for {@link RefreshMode#REQUEST_ASYN}
	 * 
	 */
	private class CacheUpdateRunner<X> implements Runnable {

		private final CacheRequest<V> request;

		public CacheUpdateRunner(CacheRequest<V> request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
		}

		@Override
		public void run() {
			String cacheKey = request.getUserKey();
			try {
				V value = request.getUpdater().fetch(request);
				request.setLastRefresh(System.currentTimeMillis());
				doCachePut(request, value);
			} catch (Exception x) {
				logger.warn("Failed to update request " + cacheKey, x);
			} finally {
				updated.remove(cacheKey);
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
			setName("refresh-cache-" + CacheBase.this.getName());
		}

		@Override
		public void run() {
			while (keepGoing) {
				try {
					Thread.sleep(interval);
				} catch (InterruptedException ix) {
					if (keepGoing) {
						logger.debug("RefreshSchedulerThread stopped from sleep");
						return;
					} else {
						logger.warn("RefreshSchedulerThread interrupted but continue");
					}
				}
				try {
					check();
				} catch (Exception x) {
					logger.warn("Exception during check", x);
				}
			}
			logger.debug("RefreshSchedulerThread stopped from work");
		}

		private void check() {
			long now = System.currentTimeMillis();
			logger.debug("check");
			for (Entry<String, CacheRequest<V>> entry : scheduled.entrySet()) {
				try {
					CacheRequest request = entry.getValue();
					if (request.getSoftExpire() < now) {
						startRefresh(request);
					}
				} catch (Exception x) {
					logger.warn("Exception during refresh of " + entry.getValue(), x);
				}
			}
		}
	}

}
