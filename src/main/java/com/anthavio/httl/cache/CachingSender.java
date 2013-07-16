package com.anthavio.httl.cache;

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

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.cache.CacheBase;
import com.anthavio.cache.CacheEntry;
import com.anthavio.httl.ExtractionOperations;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.SenderException;
import com.anthavio.httl.SenderHttpStatusException;
import com.anthavio.httl.SenderOperations;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.cache.CachingRequestBuilders.CachingRequestBuilder;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.util.Cutils;
import com.anthavio.httl.util.HttpHeaderUtil;

/**
 * Sender warpper that caches Responses as they are recieved from remote server. Response body is stored in string or byte array.
 * 
 * Only Responses are cached and extraction of the Response is performed every call.
 * 
 * For extraction result caching use CachingExtractor
 * 
 * For asynchronous/scheduled cache refreshing, executor service must be set.
 * 
 * 
 * @author martin.vanek
 *
 */
public class CachingSender implements SenderOperations, ExtractionOperations {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSender sender;

	private final CacheBase<CachedResponse> cache;

	private Map<String, CachingRequest> refresh = new HashMap<String, CachingRequest>();

	private ExecutorService executor;

	private Map<String, CachingRequest> scheduled = new HashMap<String, CachingRequest>();

	private RefreshSchedulerThread scheduler;

	public CachingSender(HttpSender sender, CacheBase<CachedResponse> cache) {
		this(sender, cache, null);
	}

	public CachingSender(HttpSender sender, CacheBase<CachedResponse> cache, ExecutorService executor) {
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
			this.executor = sender.getExecutor(); //can be null too
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
	public CacheBase<CachedResponse> getCache() {
		return cache;
	}

	/**
	 * Start fluent builder
	 */
	public CachingRequestBuilder request(SenderRequest request) {
		return new CachingRequestBuilder(this, request);
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
	 * Static caching based on specified TTL
	 */
	public CacheEntry<CachedResponse> execute(CachingRequest request) {
		String cacheKey = getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request.getSenderRequest());
			if (!entry.isSoftExpired()) {
				return entry; //fresh hit
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
						asyncRefresh(cacheKey, request); //start asynchronous refresh
						addScheduled(cacheKey, request); //register as scheduled
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
				if (entry != null) {
					entry.getValue().setRequest(request.getSenderRequest());
					if (!entry.isSoftExpired()) {
						return entry.getValue(); //nice hit
					} else {
						logger.debug("Request soft expired " + cacheKey);
						//soft expired - refresh needed
						if (request.isAsyncRefresh()) {
							//we will return soft expired value, but we will also start asynchronous refresh
							asyncRefresh(cacheKey, request);
							if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
								addScheduled(request, cacheKey);
							}
							logger.debug("Request soft expired value returned " + cacheKey);
							return entry.getValue();
						} else { //sync update
							logger.debug("Request sync refresh start " + cacheKey);
							try {
								refresh.put(cacheKey, request);
								SenderResponse response = sender.execute(request.getSenderRequest());
								request.setLastRefresh(System.currentTimeMillis());
								return doCachePut(cacheKey, request, response);
							} catch (Exception x) {
								logger.warn("Request refresh failed for " + request, x);
								//bugger - but we still have our soft expired value
								logger.debug("Request soft expired value returned " + cacheKey);
								return entry.getValue();
							} finally {
								refresh.remove(cacheKey);
							}
						}
					}
				} else { //null entry -> execute request, extract response and put it into cache
					SenderResponse response = sender.execute(request.getSenderRequest());
					request.setLastRefresh(System.currentTimeMillis());
					if (request.getRefreshMode() == RefreshMode.SCHEDULED) {
						addScheduled(request, cacheKey);
					}
					return doCachePut(cacheKey, request, response);
				}
				*/
	}

	public SenderResponse execute(SenderRequest request, int ttl, TimeUnit unit) {
		return execute(new CachingRequest(request, ttl, unit)).getValue();
	}

	private void asyncRefresh(String cacheKey, CachingRequest request) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous refresh is not configured");
		}
		synchronized (refresh) {
			if (refresh.containsKey(cacheKey)) {
				logger.debug("Async refresh already running " + cacheKey);
			} else {
				logger.debug("Async refresh start " + cacheKey);
				executor.execute(new RefreshRunnable<Serializable>(request));
			}
		}
	}

	private CacheEntry<CachedResponse> refresh(String cacheKey, CachingRequest request) {
		CacheEntry<CachedResponse> entry;
		try {
			refresh.put(cacheKey, request);
			SenderResponse response = sender.execute(request.getSenderRequest());
			request.setLastRefresh(System.currentTimeMillis());
			CachedResponse cached = new CachedResponse(request.getSenderRequest(), response);
			entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(), request.getSoftTtl());
			if (response.getHttpStatusCode() < 300) {
				cache.set(cacheKey, entry);
			}
		} finally {
			refresh.remove(cacheKey);
		}
		return entry;
	}

	/**
	 * Schedule this CachingRequest to be updated(refreshed) automatically in the background. 
	 * 
	 * Also starts scheduler thread if it is not running yet.
	 */
	private <T> void addScheduled(String cacheKey, CachingRequest request) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous refresh is not configured");
		}
		if (scheduled.get(cacheKey) != null) {
			logger.debug("Request is already scheduled to refresh " + cacheKey);
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
	 * Puts Response into Cache if http status < 300
	 
	private SenderResponse doCachePut(String cacheKey, CachingRequest request, SenderResponse response) {
		if (response.getHttpStatusCode() < 300) {
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
	*/
	/**
	 * Static caching based on specified TTL
	 */
	public <T> ExtractedBodyResponse<T> extract(CachingRequest request, Class<T> resultType) {
		SenderResponse response = execute(request).getValue();
		try {
			T extracted = sender.extract(response, resultType);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Static caching based on specified TTL
	 */
	public <T> ExtractedBodyResponse<T> extract(CachingRequest request, ResponseBodyExtractor<T> extractor) {
		SenderResponse response = execute(request).getValue();
		try {
			T extracted = extractor.extract(response);
			return new ExtractedBodyResponse<T>(response, extracted);

		} catch (IOException iox) {
			throw new SenderException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Caching based on HTTP response headers
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, ResponseBodyExtractor<T> extractor) {
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor is null");
		}
		SenderResponse response = execute(request);
		try {
			if (response.getHttpStatusCode() >= 300) {
				throw new SenderHttpStatusException(response);
			}
			T extracted = extractor.extract(response);
			return new ExtractedBodyResponse<T>(response, extracted);

		} catch (IOException iox) {
			throw new SenderException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Caching based on HTTP response headers
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType) {
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
	 * Caching based on HTTP headers values (ETag, Last-Modified, Cache-Control, Expires).
	 * 
	 * Caller is responsible for closing Response.
	 */
	public SenderResponse execute(SenderRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		String cacheKey = sender.getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request);
			if (!entry.isSoftExpired()) {
				return entry.getValue(); //cache hit and not soft expired - hurray
			} else {
				//soft expired - verify freshness
				String etag = entry.getValue().getFirstHeader("ETag");
				if (etag != null) { //ETag
					request.setHeader("If-None-Match", etag); //XXX this modifies request so hashCode will change as well
				}
				String lastModified = entry.getValue().getFirstHeader("Last-Modified");
				if (lastModified != null) { //Last-Modified
					request.setHeader("If-Modified-Since", lastModified);
				}
			}
		} else if (request.getFirstHeader("If-None-Match") != null) {
			throw new IllegalStateException("Cannot use request ETag without holding cached response");
		}
		SenderResponse response = sender.execute(request);

		if (response.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
			//only happen when we sent Etag => we have CacheEntry
			//entry.setServerDate(response.getFirstHeader("Date"));
			Cutils.close(response);
			return entry.getValue();
		}

		if (response.getHttpStatusCode() < 300) {
			CacheEntry<CachedResponse> entryNew = HttpHeaderUtil.buildCacheEntry(request, response);
			if (entryNew != null) {
				cache.set(cacheKey, entryNew);
				return entryNew.getValue();
			} else {
				logger.info("Response http headers do not allow caching");
				return response;
			}

		} else {
			//Other then HTTP 200 OK responses are NOT cached
			return response;
		}
	}

	/**
	 * Stops scheduler thread if running
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
				+ refresh.size() + ", scheduled=" + scheduled.size() + "]";
	}

	/**
	 * Runnable for {@link RefreshMode#ASYNC}
	 * 
	 */
	private class RefreshRunnable<T> implements Runnable {

		private final CachingRequest request;

		public RefreshRunnable(CachingRequest request) {
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
				logger.warn("Failed to refresh " + cacheKey, x);
			} finally {
				refresh.remove(cacheKey);
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
			setName("scheduler-cs-" + sender.getConfig().getHostUrl());
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
			for (Entry<String, CachingRequest> entry : scheduled.entrySet()) {
				try {
					CachingRequest request = entry.getValue();
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
