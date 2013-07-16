package com.anthavio.httl.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.cache.CacheBase;
import com.anthavio.cache.CacheEntry;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.cache.CachingRequestBuilders.CachingExtractorRequestBuilder;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * Sender wrapper caches product of Response extraction.
 * 
 * For asynchronous/scheduled cache refreshing, executor service must be set.
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final CacheBase<Object> cache;

	private final HttpSender sender;

	private ExecutorService executor;

	private Map<String, CachingExtractorRequest<?>> refresh = new HashMap<String, CachingExtractorRequest<?>>();

	private Map<String, CachingExtractorRequest<?>> scheduled = new HashMap<String, CachingExtractorRequest<?>>();

	private RefreshSchedulerThread scheduler;

	/**
	 * Create fully initialized CachingExtractor
	 * 
	 * @param sender underlying HttpSender
	 * @param cache underlying cache
	 * @param executor for asynchronous updates
	 */
	public CachingExtractor(HttpSender sender, CacheBase<Object> cache, ExecutorService executor) {
		if (sender == null) {
			throw new IllegalArgumentException("sender is null");
		}
		this.sender = sender;

		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		this.cache = cache;

		if (executor != null) {
			this.executor = executor;
		} else {
			this.executor = sender.getExecutor(); //can be null
		}
	}

	/**
	 * Create CachingExtractor without asychronous updates support
	 * 
	 * @param sender
	 * @param cache
	 */
	public CachingExtractor(HttpSender sender, CacheBase<Object> cache) {
		this(sender, cache, null);
	}

	/**
	 * @return underlying executor service
	 */
	public ExecutorService getExecutor() {
		return executor;
	}

	/**
	 * Sets executor service for asychronous updates
	 */
	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	/**
	 * @return underlying sender
	 */
	public HttpSender getSender() {
		return sender;
	}

	/**
	 * @return underlying cache
	 */
	public CacheBase<?> getCache() {
		return cache;
	}

	/**
	 * Close underlying scheduler (if created)
	 */
	public void close() {
		if (this.scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}
	}

	/**
	 * Fancy toString
	 */
	@Override
	public String toString() {
		return "CachingExtractor [url=" + sender.getConfig().getHostUrl() + ", executor=" + executor + ", updating="
				+ refresh.size() + ", scheduled=" + scheduled.size() + "]";
	}

	/**
	 * Start fluent builder
	 */
	public <T> CachingExtractorRequestBuilder request(SenderRequest request) {
		return new CachingExtractorRequestBuilder(this, request);
	}

	/**
	 * Custom cache key from request (if exist) takes precedence
	 * Otherwise key derived from request URL is used
	 */
	protected String getCacheKey(CachingRequest request) {
		String cacheKey = request.getCacheKey();
		if (cacheKey == null) {
			cacheKey = sender.getCacheKey(request.getSenderRequest());
		}
		return cacheKey;
	}

	/**
	 * Extracted response version. Response is extracted, then closed and result is returned to caller
	 * Static caching based on specified amount and unit
	 */
	public <T> CacheEntry<T> extract(CachingExtractorRequest<T> request) {
		String cacheKey = getCacheKey(request);
		CacheEntry<T> entry = (CacheEntry<T>) cache.get(cacheKey); //XXX expect ClassCastException
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return (CacheEntry<T>) entry; //fresh hit
			} else {
				//soft expired - refresh needed
				logger.debug("Soft expired " + cacheKey);
				RefreshMode mode = request.getRefreshMode();
				if (mode == RefreshMode.BLOCK) {
					//logger.debug("Sync refresh start " + cacheKey);
					return refresh(cacheKey, request);
				} else if (mode == RefreshMode.ASYNC || mode == RefreshMode.RETURN) {
					asyncRefresh(cacheKey, request); //start asynchronous refresh
					logger.debug("Soft expired value returned " + cacheKey);
					return entry; //return soft expired value
				} else if (mode == RefreshMode.SCHEDULED) {
					if (scheduled.get(cacheKey) == null) {
						asyncRefresh(cacheKey, request);
						addScheduled(cacheKey, request);
					}
					logger.debug("Soft expired value returned " + cacheKey);
					return entry; //return soft expired value
				} else {
					throw new IllegalArgumentException("Unknown RefreshMode " + mode);
				}
			}
		} else { //cache miss - we have nothing
			RefreshMode mode = request.getRefreshMode();
			if (mode == RefreshMode.BLOCK || mode == RefreshMode.RETURN) {
				return refresh(cacheKey, request);
			} else if (mode == RefreshMode.ASYNC) {
				asyncRefresh(cacheKey, request);
				return null;
			} else if (mode == RefreshMode.SCHEDULED) {
				if (scheduled.get(cacheKey) == null) {
					asyncRefresh(cacheKey, request);
					addScheduled(cacheKey, request);
				}
				return null;
			} else {
				throw new IllegalArgumentException("Unknown RefreshMode " + mode);
			}
		}
		/*
		String cacheKey = getCacheKey(request);
		CacheEntry<?> entry = cache.get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return (T) entry.getValue(); //nice nonexpired hit
			} else {
				//soft expired -  refresh needed
				logger.debug("Request soft expired " + cacheKey);
				if (request.isAsyncRefresh()) {
					//we will return soft expired value, but we will also start asynchronous refresh
					asyncRefresh(cacheKey, request);

					if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
						addScheduled(request, cacheKey);
					}
					logger.debug("Request soft expired value returned " + cacheKey);
					return (T) entry.getValue();
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						refresh.put(cacheKey, request); //unsafe, but I don't want to lock around sender.extract() method
						ExtractedBodyResponse<T> extract = doExtract(request, cacheKey);
						return extract.getBody();
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - but we still have our soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return (T) entry.getValue();
					} finally {
						refresh.remove(cacheKey);
					}
				}
			}
		} else { //entry is null -> execute request, extract response and put it into cache
			ExtractedBodyResponse<T> extracted = doExtract(request, cacheKey);
			if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
				addScheduled(request, cacheKey);
			}
			return extracted.getBody();
		}
		*/
	}

	/**
	 * Extract response and store product in cache
	 */
	private <T> CacheEntry<T> refresh(String cacheKey, CachingExtractorRequest<T> request) {
		CacheEntry<T> entry;
		ExtractedBodyResponse<T> extracted;
		if (request.getExtractor() != null) {
			extracted = sender.extract(request.getSenderRequest(), request.getExtractor());
		} else {
			extracted = sender.extract(request.getSenderRequest(), request.getResultType());
		}
		request.setLastRefresh(System.currentTimeMillis());
		entry = new CacheEntry<T>(extracted.getBody(), request.getHardTtl(), request.getSoftTtl());
		cache.set(cacheKey, (CacheEntry<Object>) entry);
		return entry;
	}

	private <T> void asyncRefresh(String cacheKey, CachingExtractorRequest<T> request) {
		synchronized (refresh) {
			if (refresh.containsKey(cacheKey)) {
				logger.debug("Async refresh already running " + cacheKey);
			} else {
				logger.debug("Async refresh start " + cacheKey);
				executor.execute(new RefreshRunnable<T>(request));
			}
		}
	}

	/**
	 * Schedule new request for automatic refresh
	 */
	private <T> void addScheduled(String cacheKey, CachingExtractorRequest<T> request) {
		//schedule if not already scheduled
		if (scheduled.get(cacheKey) != null) {
			logger.debug("Request is already scheduled to refresh" + cacheKey);
		} else {
			scheduled.put(cacheKey, request);
			logger.debug("Request is now scheduled to be refreshed " + cacheKey);
			synchronized (this) {
				if (scheduler == null) {
					scheduler = new RefreshSchedulerThread(1, TimeUnit.SECONDS);
					scheduler.start();
				}
			}
		}
	}

	/**
	 * Runnable for {@link RefreshMode#ASYNC}
	 * 
	 */
	private class RefreshRunnable<T> implements Runnable {

		private final CachingExtractorRequest<T> request;

		public RefreshRunnable(CachingExtractorRequest<T> request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
		}

		@Override
		public void run() {
			String cacheKey = getCacheKey(request);
			try {
				refresh(cacheKey, request);
			} catch (Exception x) {
				logger.warn("Failed to refresh " + request, x);
			} finally {
				refresh.remove(cacheKey);
			}
		}

	}

	/**
	 * Thread for {@link RefreshMode#SCHEDULED}
	 * 
	 * @author martin.vanek
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
			setName("scheduler-ce-" + sender.getConfig().getHostUrl());
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
			logger.debug("check");

			for (Entry<String, CachingExtractorRequest<?>> entry : scheduled.entrySet()) {
				try {
					CachingExtractorRequest<?> request = entry.getValue();
					if (request.getSoftExpire() < now) {
						String cacheKey = getCacheKey(request);
						asyncRefresh(cacheKey, request);
					}
				} catch (Exception x) {
					logger.warn("Exception during refresh of " + entry.getValue(), x);
				}
			}

		}
	}

}
