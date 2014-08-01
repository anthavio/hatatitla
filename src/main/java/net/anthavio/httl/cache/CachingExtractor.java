package net.anthavio.httl.cache;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.cache.CacheEntryLoader;
import net.anthavio.cache.CacheLoadRequest;
import net.anthavio.cache.CachingSettings;
import net.anthavio.cache.ConfiguredCacheLoader;
import net.anthavio.cache.ConfiguredCacheLoader.SimpleLoader;
import net.anthavio.cache.LoadingSettings;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.cache.Builders.ExtractingRequestBuilder;

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
public class CachingExtractor /*implements SenderOperations, ExtractionOperations*/{

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttlSender sender;

	private final CacheBase<Serializable> cache;

	public CachingExtractor(HttlSender sender, CacheBase<Serializable> cache) {
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
	public HttlSender getSender() {
		return sender;
	}

	/**
	 * @return underlying cache
	 */
	public CacheBase<Serializable> getCache() {
		return cache;
	}

	/**
	 * FIXME - this is silly method name
	 * Start fluent builder
	 */
	public ExtractingRequestBuilder from(HttlRequest request) {
		return new ExtractingRequestBuilder(this, request);
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

	public static class SimpleHttpSenderExtractor<T extends Serializable> implements SimpleLoader<T> {

		private final HttlSender sender;

		private final CachingExtractorRequest<T> request;

		public SimpleHttpSenderExtractor(HttlSender sender, CachingExtractorRequest<T> request) {
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
		public T load() throws Exception {
			ExtractedResponse<T> bodyResponse;
			if (request.getExtractor() != null) {
				bodyResponse = sender.extract(request.getSenderRequest(), request.getExtractor());
			} else {
				bodyResponse = sender.extract(request.getSenderRequest(), request.getResultType());
			}
			return bodyResponse.getPayload();
		}

	}

	/**
	 * Turns Sender Request into Cache Request
	 */
	public <T extends Serializable> CacheLoadRequest<T> convert(CachingExtractorRequest<T> request) {
		String cacheKey = getCacheKey(request);
		CachingSettings caching = new CachingSettings(cacheKey, request.getEvictTtl(), request.getExpiryTtl(),
				TimeUnit.SECONDS);

		SimpleHttpSenderExtractor<T> simple = new SimpleHttpSenderExtractor<T>(sender, request);
		CacheEntryLoader<T> loader = new ConfiguredCacheLoader<T>(simple, request.getMissingRecipe(),
				request.getExpiredRecipe());
		LoadingSettings<T> loading = new LoadingSettings<T>(loader, request.isMissingLoadAsync(),
				request.isExpiredLoadAsync());
		return new CacheLoadRequest<T>(caching, loading);
	}

	/**
	 * Static caching based on specified TTL
	 * 
	 * XXX Having complexity of entry loading and caching on Cache side imply
	 * simplifies this class implementation
	 * causes two cache queries for expired and missing entries 
	 */
	public <T extends Serializable> CacheEntry<T> extract(CachingExtractorRequest<T> request) {
		String cacheKey = getCacheKey(request);
		CacheEntry<T> entry = (CacheEntry<T>) cache.get(cacheKey);
		if (entry != null) {
			//entry.getValue().setRequest(request.getSenderRequest());
			if (!entry.isStale()) {
				return (CacheEntry<T>) entry; //fresh hit
			} else {
				CacheLoadRequest<T> cacheRequest = convert(request);
				return (CacheEntry<T>) cache.load((CacheLoadRequest<Serializable>) cacheRequest,
						(CacheEntry<Serializable>) entry);
				//return (CacheEntry<T>) cache.get(lrequest);
			}
		} else { //cache miss - we have nothing
			CacheLoadRequest<T> cacheRequest = convert(request);
			return (CacheEntry<T>) cache.load((CacheLoadRequest<Serializable>) cacheRequest, null);
		}
	}

	/**
	 * Static caching based on specified TTL
	 
	public <T> ExtractedBodyResponse<T> extract(CachingSenderRequest request, Class<T> resultType) {
		SenderResponse response = execute(request).getValue();
		try {
			T extracted = sender.extract(response, resultType);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}
	*/
	/**
	 * Static caching based on specified TTL
	 
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
	*/
	/**
	 * Caching based on HTTP response headers
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 
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
	*/
	/**
	 * Caching based on HTTP response headers
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 
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
	*/
	/**
	 * Caching based on HTTP headers values (ETag, Last-Modified, Cache-Control, Expires).
	 * 
	 * Caller is responsible for closing Response.
	 
	public SenderResponse execute(SenderRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		String cacheKey = sender.getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			entry.getValue().setRequest(request);
			if (!entry.isExpired()) {
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
	*/
	@Override
	public String toString() {
		return "CachingSender [url=" + sender.getConfig().getUrl() + "]";
	}

}
