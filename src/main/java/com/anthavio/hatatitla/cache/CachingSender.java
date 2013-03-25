package com.anthavio.hatatitla.cache;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.hatatitla.Cutils;
import com.anthavio.hatatitla.HttpDateUtil;
import com.anthavio.hatatitla.HttpHeaderUtil;
import com.anthavio.hatatitla.HttpSender;
import com.anthavio.hatatitla.SenderHttpStatusException;
import com.anthavio.hatatitla.SenderRequest;
import com.anthavio.hatatitla.SenderResponse;
import com.anthavio.hatatitla.cache.CachingRequest.RefreshMode;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * Sender warpper that caches Responses as they are recieved from remote server
 * 
 * Only Responses are cached and extraction of the Response is performed every call
 * 
 * For Extracted responses caching use CachingExtractor
 * 
 * 
 * @author martin.vanek
 *
 */
public class CachingSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSender sender;

	private final RequestCache<CachedResponse> cache;

	private Map<String, CachingRequest> updated = new HashMap<String, CachingRequest>();

	private ExecutorService executor;

	private Map<String, CachingRequest> scheduled = new HashMap<String, CachingRequest>();

	private RefreshSchedulerThread scheduler;

	private boolean cacheNon200 = false;

	public CachingSender(HttpSender sender, RequestCache<CachedResponse> cache) {
		this(sender, cache, null);
	}

	public CachingSender(HttpSender sender, RequestCache<CachedResponse> cache, ExecutorService executor) {
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
	public RequestCache<CachedResponse> getCache() {
		return cache;
	}

	public SenderResponse execute(CachingRequest request) throws IOException {
		if (request.isAsyncRefresh() && this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		String cacheKey = sender.getCacheKey(request.getSenderRequest());
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request.getSenderRequest());
			if (!entry.isSoftExpired()) {
				return entry.getValue(); //nice hit
			} else {
				logger.debug("Request soft expired " + cacheKey);
				//soft expired -  refresh needed
				if (request.isAsyncRefresh()) {
					//we will return soft expired value, but we will also start asynchronous refresh
					startRefresh(cacheKey, request);
					if (request.getRefreshMode() == RefreshMode.ASYNC_SCHEDULE) {
						doScheduled(request, cacheKey);
					}
					logger.debug("Request soft expired value returned " + cacheKey);
					return entry.getValue();
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						updated.put(cacheKey, request);
						SenderResponse response = sender.execute(request.getSenderRequest());
						request.setLastRefresh(System.currentTimeMillis());
						return doCache(cacheKey, request, response);
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - but we still have our soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return entry.getValue();
					} finally {
						updated.remove(cacheKey);
					}
				}
			}
		} else { //entry is null -> execute request, extract response and put it into cache
			SenderResponse response = sender.execute(request.getSenderRequest());
			request.setLastRefresh(System.currentTimeMillis());
			if (request.getRefreshMode() == RefreshMode.ASYNC_SCHEDULE) {
				doScheduled(request, cacheKey);
			}
			return doCache(cacheKey, request, response);
		}
	}

	private void startRefresh(String cacheKey, CachingRequest request) {
		synchronized (updated) {
			if (updated.containsKey(cacheKey)) {
				logger.debug("Request is already being refreshed " + cacheKey);
			} else {
				logger.debug("Request async refresh start " + cacheKey);
				executor.execute(new CacheUpdateRunner<Serializable>(request));
				updated.put(cacheKey, request);
			}
		}
	}

	private <T extends Serializable> void doScheduled(CachingRequest request, String cacheKey) {
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

	private SenderResponse doCache(String cacheKey, CachingRequest request, SenderResponse response) throws IOException {
		if (response.getHttpStatusCode() == HttpURLConnection.HTTP_OK || cacheNon200) {
			CachedResponse cached = new CachedResponse(request.getSenderRequest(), response);
			CacheEntry<CachedResponse> entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(),
					request.getSoftTtl());
			cache.set(cacheKey, entry);
			return cached;
		} else {
			//logger.info("Not putting non http 200 request to cache");
			return response;
		}
	}

	/**
	 * Static caching based on specified ttl and unit
	 */
	public SenderResponse execute(SenderRequest request, long ttl, TimeUnit unit) throws IOException {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		return execute(new CachingRequest(request, ttl, unit));
	}

	/**
	 * Caching based on HTTP headers values
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T extends Serializable> ExtractedBodyResponse<T> extract(SenderRequest request,
			ResponseBodyExtractor<T> extractor) throws IOException {
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor is null");
		}
		SenderResponse response = execute(request);
		try {
			if (response.getHttpStatusCode() >= 300) {
				HttpHeaderUtil.readAsString(response);
				throw new SenderHttpStatusException(response);
			}
			T extracted = extractor.extract(response);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Caching based on HTTP headers values
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T extends Serializable> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType)
			throws IOException {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		SenderResponse response = execute(request);
		try {
			T extracted = sender.extract(response, resultType);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Caching based on HTTP headers values (ETag, Last-Modified, Cache-Control, Expires)
	 */
	public SenderResponse execute(SenderRequest request) throws IOException {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		String cacheKey = sender.getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request);
			if (!entry.isSoftExpired()) {
				return entry.getValue(); //cache hit and not soft expired
			} else {
				//soft expired - verify freshness
				if (entry.getServerTag() != null) { //ETag
					request.setHeader("If-None-Match", entry.getServerTag()); //XXX this modifies request so hashCode will change as well
				}
				if (entry.getServerDate() != null) { //Last-Modified
					request.setHeader("If-Modified-Since", HttpDateUtil.formatDate(entry.getServerDate()));
				}
			}
		} else if (request.getFirstHeader("If-None-Match") != null) {
			throw new IllegalStateException("Cannot use request ETag without holding cached response");
		}
		SenderResponse response = sender.execute(request);

		if (response.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
			//only happen when we sent Etag => we have CacheEntry
			//entry.setServerDate(response.getFirstHeader("Date"));
			return entry.getValue();
		}

		if (response.getHttpStatusCode() == HttpURLConnection.HTTP_OK || cacheNon200) {
			CacheEntry<CachedResponse> entryNew = HttpHeaderUtil.buildCacheEntry(request, response);
			if (entryNew != null) {
				cache.set(cacheKey, entryNew);
				return entryNew.getValue();
			} else {
				logger.info("Response http headers disallows caching");
				return response;
			}

		} else {
			//Other then HTTP 200 OK responses are NOT cached
			return response;
		}
	}

	/**
	 * Close undelying sender and cache 
	 */
	public void close() {
		if (this.scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}
	}

	@Override
	public String toString() {
		return "CachingSender [url=" + sender.getConfig().getHostUrl() + ", executor=" + executor + ", updating="
				+ updated.size() + ", scheduled=" + scheduled.size() + "]";
	}

	/**
	 * Runnable for {@link RefreshMode#ASYNC_REQUEST}
	 * 
	 */
	private class CacheUpdateRunner<T extends Serializable> implements Runnable {

		private final CachingRequest request;

		public CacheUpdateRunner(CachingRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
		}

		@Override
		public void run() {
			String cacheKey = sender.getCacheKey(request.getSenderRequest());
			try {
				SenderResponse response = sender.execute(request.getSenderRequest());
				request.setLastRefresh(System.currentTimeMillis());
				doCache(cacheKey, request, response);
			} catch (Exception x) {
				logger.warn("Failed to update request " + cacheKey, x);
			} finally {
				updated.remove(cacheKey);
			}
		}
	}

	/**
	 * Thread for {@link RefreshMode#ASYNC_SCHEDULE}
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
			setName("refresh-cs-" + sender.getConfig().getHostUrl());
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
			for (Entry<String, CachingRequest> entry : scheduled.entrySet()) {
				try {
					CachingRequest request = entry.getValue();
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
