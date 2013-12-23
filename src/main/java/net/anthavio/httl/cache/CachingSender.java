package net.anthavio.httl.cache;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.cache.CacheEntryLoader;
import net.anthavio.cache.CacheLoadRequest;
import net.anthavio.cache.CachingSettings;
import net.anthavio.cache.ConfiguredCacheLoader;
import net.anthavio.cache.ConfiguredCacheLoader.SimpleLoader;
import net.anthavio.cache.LoadingSettings;
import net.anthavio.httl.ExtractionOperations;
import net.anthavio.httl.HttpSender;
import net.anthavio.httl.SenderException;
import net.anthavio.httl.SenderHttpStatusException;
import net.anthavio.httl.SenderOperations;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.cache.Builders.CachingRequestBuilder;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sender wrapper that caches Responses as they are recieved from remote server. 
 * Server response bodies are cached as string or byte array and extraction performed every call.
 * 
 * TODO reintroduce CachingExtractor - Note: If you want to cache extraction product, use CachingExtractor
 * 
 * Note: For automatic updates use CacheBase#schedule
 * 
 * @author martin.vanek
 *
 */
public class CachingSender implements SenderOperations, ExtractionOperations {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSender sender;

	private final CacheBase<CachedResponse> cache;

	public CachingSender(HttpSender sender, CacheBase<CachedResponse> cache) {
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
	 * FIXME - this is silly method name
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

	public static class SimpleHttpSenderLoader implements SimpleLoader<CachedResponse> {

		private HttpSender sender;

		private SenderRequest request;

		public SimpleHttpSenderLoader(HttpSender sender, SenderRequest request) {
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
		public CachedResponse load() throws Exception {
			SenderResponse response = sender.execute(this.request);
			return new CachedResponse(this.request, response);
		}

	}

	/**
	 * Turns Sender Request into Cache Request
	 */
	private CacheLoadRequest<CachedResponse> convert(CachingSenderRequest request) {
		CachingSettings caching = new CachingSettings(request.getUserKey(), request.getHardTtl(), request.getSoftTtl(),
				TimeUnit.SECONDS);
		SimpleHttpSenderLoader simple = new SimpleHttpSenderLoader(sender, request.getSenderRequest());
		CacheEntryLoader<CachedResponse> loader = new ConfiguredCacheLoader<CachedResponse>(simple,
				request.getMissingRecipe(), request.getExpiredRecipe());
		LoadingSettings<CachedResponse> loading = new LoadingSettings<CachedResponse>(loader, request.isMisLoadAsync(),
				request.isExpLoadAsync());
		return new CacheLoadRequest<CachedResponse>(caching, loading);
	}

	/**
	 * Static caching based on specified TTL
	 * 
	 * XXX Having complexity of entry loading and caching on Cache side imply
	 * simplifies this class implementation
	 * causes two cache queries for expired and missing entries 
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

	@Override
	public String toString() {
		return "CachingSender [url=" + sender.getConfig().getHostUrl() + "]";
	}

}
