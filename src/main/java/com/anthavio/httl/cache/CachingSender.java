package com.anthavio.httl.cache;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.cache.CacheBase;
import com.anthavio.cache.CacheEntry;
import com.anthavio.cache.CacheEntryLoader;
import com.anthavio.cache.CacheEntryLoader.LoadResult;
import com.anthavio.cache.CacheLoadRequest;
import com.anthavio.cache.CachingSettings;
import com.anthavio.cache.ConfiguredCacheLoader;
import com.anthavio.cache.ConfiguredCacheLoader.MissingExceptionSettings;
import com.anthavio.cache.LoadingSettings;
import com.anthavio.httl.ExtractionOperations;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.SenderException;
import com.anthavio.httl.SenderHttpStatusException;
import com.anthavio.httl.SenderOperations;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.cache.Builders.CachingRequestBuilder;
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

	//private Map<String, CachingSenderRequest> refresh = new HashMap<String, CachingSenderRequest>();

	//private ExecutorService executor;

	//private Map<String, CachingSenderRequest> scheduled = new HashMap<String, CachingSenderRequest>();

	//private RefreshSchedulerThread scheduler;

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
	public CachingRequestBuilder from(SenderRequest request) {
		return new CachingRequestBuilder(this, request);
	}

	/**
	 * Custom cache key from request (if exist) takes precedence
	 * Otherwise key derived from request URL is used
	 */
	protected String getCacheKey(CachingSenderRequest request) {
		String cacheKey = request.getUserKey();
		if (cacheKey == null) {
			cacheKey = sender.getCacheKey(request.getSenderRequest());
		}
		return cacheKey;
	}

	/*
		public SenderResponse execute(SenderRequest request, int ttl, TimeUnit unit) {
			return execute(new CachingSenderRequest(request, ttl, unit)).getValue();
		}
	*/
	public static class HttpSenderLoader extends ConfiguredCacheLoader<CachedResponse> {

		private HttpSender sender;

		private SenderRequest request;

		public HttpSenderLoader(HttpSender sender, SenderRequest request, MissingExceptionSettings sync,
				MissingExceptionSettings async) {
			if (sender == null) {
				throw new IllegalArgumentException("Null sender");
			}
			this.sender = sender;

			if (request == null) {
				throw new IllegalArgumentException("Null request");
			}
			this.request = request;
		}

		@Override
		protected LoadResult<CachedResponse> load() throws Exception {
			SenderResponse response = sender.execute(this.request);
			CachedResponse cresponse = new CachedResponse(this.request, response);
			return new LoadResult<CachedResponse>(cresponse);
		}
	}

	private CacheLoadRequest<CachedResponse> convert(CachingSenderRequest request) {
		CachingSettings caching = new CachingSettings(request.getUserKey(), request.getHardTtl(), request.getSoftTtl(),
				TimeUnit.SECONDS);
		CacheEntryLoader<CachedResponse> loader = new HttpSenderLoader(sender, request.getSenderRequest(),
				request.getSyncExceptionSettings(), request.getAsyncExceptionSettings());
		LoadingSettings<CachedResponse> loading = new LoadingSettings<CachedResponse>(loader, request.isMissingLoadAsync(),
				request.isExpiredLoadAsync());
		return new CacheLoadRequest<CachedResponse>(caching, loading);
	}

	/**
	 * Static caching based on specified TTL
	 */
	public CacheEntry<CachedResponse> execute(CachingSenderRequest request) {
		String cacheKey = getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request.getSenderRequest());
			if (!entry.isSoftExpired()) {
				return entry; //fresh hit
			} else {
				CacheLoadRequest<CachedResponse> lrequest = convert(request);
				return cache.get(lrequest);
			}
		} else { //cache miss - we have nothing
			CacheLoadRequest<CachedResponse> lrequest = convert(request);
			return cache.get(lrequest);
		}
	}

	/*
		private void asyncRefresh(String cacheKey, CachingSenderRequest request) {
			if (this.executor == null) {
				throw new IllegalStateException("Executor for asynchronous refresh is not configured");
			}
			synchronized (refresh) {
				if (refresh.containsKey(cacheKey)) {
					logger.debug("Async refresh already running " + cacheKey);
				} else {
					logger.debug("Async refresh starting " + cacheKey);
					try {
						executor.execute(new RefreshRunnable<Serializable>(request));
					} catch (RejectedExecutionException rx) {
						logger.warn("Async refresh rejected " + rx.getMessage());
					} catch (Exception x) {
						logger.error("Async refresh start failed " + cacheKey, x);
					}
				}
			}
		}

		private CacheEntry<CachedResponse> refresh(String cacheKey, CachingSenderRequest request) {
			CacheEntry<CachedResponse> entry;
			try {
				refresh.put(cacheKey, request);
				SenderResponse response = sender.execute(request.getSenderRequest());
				request.setLastRefresh(System.currentTimeMillis());
				CachedResponse cached = new CachedResponse(request.getSenderRequest(), response);
				entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(), request.getSoftTtl());
				//cache only 20x responses 
				if (response.getHttpStatusCode() < 300) {
					cache.set(cacheKey, entry);
				}
			} finally {
				refresh.remove(cacheKey);
			}
			return entry;
		}
	*/
	/**
	 * Schedule this CachingRequest to be updated(refreshed) automatically in the background. 
	 * 
	 * Also starts scheduler thread if it is not running yet.
	 
	private <T> void addScheduled(String cacheKey, CachingSenderRequest request) {
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
	*/
	/**
	 * Static caching based on specified TTL
	 */
	public <T> ExtractedBodyResponse<T> extract(CachingSenderRequest request, Class<T> resultType) {
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
	public <T> ExtractedBodyResponse<T> extract(CachingSenderRequest request, ResponseBodyExtractor<T> extractor) {
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
	 
	public void close() {
		if (this.scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}
	}
	*/
	@Override
	public String toString() {
		return "CachingSender [url=" + sender.getConfig().getHostUrl() + "]";
	}

	/**
	 * Runnable for {@link LoadMode#ASYNC}
	 * 
	 
	private class RefreshRunnable<T> implements Runnable {

		private final CachingSenderRequest request;

		public RefreshRunnable(CachingSenderRequest request) {
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
	*/
	/**
	 * Thread for {@link LoadMode#SCHEDULED}
	 * 
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
			for (Entry<String, CachingSenderRequest> entry : scheduled.entrySet()) {
				try {
					CachingSenderRequest request = entry.getValue();
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
	*/

}
