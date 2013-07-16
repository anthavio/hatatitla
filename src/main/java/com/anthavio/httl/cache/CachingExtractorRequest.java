package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.cache.CachingRequestBuilders.CachingExtractorRequestBuilder;
import com.anthavio.httl.inout.ResponseBodyExtractor;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<T> extends CachingRequest {

	public static CachingExtractorRequestBuilder Builder(CachingExtractor cextractor, SenderRequest request) {
		return new CachingExtractorRequestBuilder(cextractor, request);
	}

	private final ResponseBodyExtractor<T> extractor;

	private final Class<T> resultType;

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, TimeUnit unit) {
		this(request, extractor, hardTtl, hardTtl, unit, RefreshMode.BLOCK, null); //hardTtl = softTtl
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl,
			TimeUnit unit, RefreshMode refreshMode) {
		this(request, extractor, hardTtl, hardTtl, unit, refreshMode, null);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit) {
		this(request, extractor, hardTtl, softTtl, unit, RefreshMode.BLOCK, null);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit, RefreshMode refreshMode) {
		this(request, extractor, hardTtl, softTtl, unit, refreshMode, null);
	}

	/**
	 * Full constructor - Use CachingExtractorRequestBuilder instead of this...
	 * 
	 * @param request - request to request a request
	 * @param extractor - response extractor
	 * @param hardTtl - cache entry expiry time 
	 * @param softTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * @param refreshMode - how to refresh stale entry 
	 * @param customCacheKey - custom cache key to be used instead of standard key derived from request url
	 * 
	 */
	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit, RefreshMode refreshMode, String customCacheKey) {
		super(request, hardTtl, softTtl, unit, refreshMode, customCacheKey);

		if (extractor == null) {
			throw new IllegalArgumentException("null extractor");
		}
		this.extractor = extractor;
		this.resultType = null;
	}

	public CachingExtractorRequest(SenderRequest request, Class<T> resultType, long hardTtl, long softTtl, TimeUnit unit,
			RefreshMode refreshMode, String customCacheKey) {
		super(request, hardTtl, softTtl, unit, refreshMode, customCacheKey);

		if (resultType == null) {
			throw new IllegalArgumentException("null resultType");
		}
		this.resultType = resultType;
		this.extractor = null;
	}

	public ResponseBodyExtractor<T> getExtractor() {
		return extractor;
	}

	public Class<T> getResultType() {
		return resultType;
	}

}
