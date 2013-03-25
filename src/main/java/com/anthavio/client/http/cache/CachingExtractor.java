package com.anthavio.client.http.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.client.http.HttpSender;
import com.anthavio.client.http.SenderRequest;
import com.anthavio.client.http.cache.CachingRequest.RefreshMode;
import com.anthavio.client.http.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final RequestCache<Serializable> cache;

	private final HttpSender sender;

	private ExecutorService executor;

	private Map<String, CachingExtractorRequest<?>> updated = new HashMap<String, CachingExtractorRequest<?>>();

	private Map<String, CachingExtractorRequest<?>> scheduled = new HashMap<String, CachingExtractorRequest<?>>();

	private RefreshSchedulerThread scheduler;

	//private ReadWriteLock lock = new ReentrantReadWriteLock();

	public CachingExtractor(HttpSender sender, RequestCache<Serializable> cache, ExecutorService executor) {
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

	public CachingExtractor(HttpSender sender, RequestCache<Serializable> cache) {
		this(sender, cache, null);
	}

	public ExecutorService getExecutor() {
		return executor;
	}

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
	public RequestCache<Serializable> getCache() {
		return cache;
	}

	public <T extends Serializable> CachingExtractorRequestBuilder with(SenderRequest request) {
		return new CachingExtractorRequestBuilder(this, request);
	}

	/**
	 * Extracted response version. Response is extracted, then closed and result is returned to caller
	 * Static caching based on specified amount and unit
	 */
	public <T extends Serializable> T extract(CachingExtractorRequest<T> request) throws IOException {
		if (request.isAsyncRefresh() && this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		String cacheKey = sender.getCacheKey(request.getSenderRequest());
		CacheEntry<Serializable> entry = cache.get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return (T) entry.getValue(); //nice nonexpired hit
			} else {
				//soft expired -  refresh needed
				logger.debug("Request soft expired " + cacheKey);
				if (request.isAsyncRefresh()) {
					//we will return soft expired value, but we will also start asynchronous refresh
					startRefresh(cacheKey, request);

					if (request.getRefreshMode() == RefreshMode.ASYNC_SCHEDULE) {
						doScheduled(request, cacheKey);
					}
					logger.debug("Request soft expired value returned " + cacheKey);
					return (T) entry.getValue();
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						updated.put(cacheKey, request); //unsafe, but I don't want to lock around sender.extract() method
						ExtractedBodyResponse<T> extract = doExtract(request, cacheKey);
						return extract.getBody();
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - but we still have our soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return (T) entry.getValue();
					} finally {
						updated.remove(cacheKey);
					}
				}
			}
		} else { //entry is null -> execute request, extract response and put it into cache
			ExtractedBodyResponse<T> extracted = doExtract(request, cacheKey);
			if (request.getRefreshMode() == RefreshMode.ASYNC_SCHEDULE) {
				doScheduled(request, cacheKey);
			}
			return extracted.getBody();
		}
	}

	private <T extends Serializable> ExtractedBodyResponse<T> doExtract(CachingExtractorRequest<T> request,
			String cacheKey) throws IOException {
		ExtractedBodyResponse<T> extracted;
		if (request.getExtractor() != null) {
			extracted = sender.extract(request.getSenderRequest(), request.getExtractor());
		} else {
			extracted = sender.extract(request.getSenderRequest(), request.getResultType());
		}
		request.setLastRefresh(System.currentTimeMillis());
		CacheEntry<Serializable> entry = new CacheEntry<Serializable>(extracted.getBody(), request.getHardTtl(),
				request.getSoftTtl());
		cache.set(cacheKey, entry);
		return extracted;
	}

	private <T extends Serializable> void doScheduled(CachingExtractorRequest<T> request, String cacheKey) {
		//schedule if not already scheduled
		if (scheduled.get(cacheKey) != null) {
			logger.debug("Request is already scheduled to refresh" + cacheKey);
		} else {
			scheduled.put(cacheKey, request);
			logger.debug("Request is now scheduled to be refreshed " + cacheKey);
			if (scheduler == null) {
				logger.info("RefreshSchedulerThread started");
				scheduler = new RefreshSchedulerThread(1, TimeUnit.SECONDS);
				scheduler.start();
			}
		}
	}

	private <T extends Serializable> void startRefresh(String cacheKey, CachingExtractorRequest<T> request) {
		synchronized (updated) {
			if (updated.containsKey(cacheKey)) {
				logger.debug("Request is already being refreshed " + cacheKey);
			} else {
				logger.debug("Request async refresh start " + cacheKey);
				executor.execute(new CacheUpdateRunner<T>(request));
				updated.put(cacheKey, request);
			}
		}
	}

	/**
	 * Close undelying scheduler
	 */
	public void close() {
		if (this.scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}
	}

	@Override
	public String toString() {
		return "CachingExtractor [url=" + sender.getConfig().getHostUrl() + ", executor=" + executor + ", updating="
				+ updated.size() + ", scheduled=" + scheduled.size() + "]";
	}

	/**
	 * Runnable for {@link RefreshMode#ASYNC_REQUEST}
	 * 
	 */
	private class CacheUpdateRunner<T extends Serializable> implements Runnable {

		private final CachingExtractorRequest<T> request;

		public CacheUpdateRunner(CachingExtractorRequest<T> request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
		}

		@Override
		public void run() {
			String cacheKey = sender.getCacheKey(request.getSenderRequest());
			try {
				doExtract(request, cacheKey);
			} catch (Exception x) {
				logger.warn("Request failed to refresh " + request, x);
			} finally {
				updated.remove(cacheKey);
			}
		}

	}

	/**
	 * Thread for {@link RefreshMode#ASYNC_SCHEDULE}
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
			setName("refresh-ce-" + sender.getConfig().getHostUrl());
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

			for (Entry<String, CachingExtractorRequest<?>> entry : scheduled.entrySet()) {
				try {
					CachingExtractorRequest<?> request = entry.getValue();
					if (request.getSoftExpire() < now) {
						String cacheKey = sender.getCacheKey(request.getSenderRequest());
						startRefresh(cacheKey, request);
					}
				} catch (Exception x) {
					logger.warn("Exception during refresh of " + entry.getValue(), x);
				}
			}

		}
	}

}
