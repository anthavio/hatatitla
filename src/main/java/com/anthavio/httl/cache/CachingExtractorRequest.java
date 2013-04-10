package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.inout.ResponseBodyExtractor;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<T> extends CachingRequest {

	private final ResponseBodyExtractor<T> extractor;

	private final Class<T> resultType;

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, TimeUnit unit) {
		this(request, extractor, hardTtl, hardTtl, unit, RefreshMode.REQUEST_SYNC); //hardTtl = softTtl
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl,
			TimeUnit unit, RefreshMode refreshMode) {
		this(request, extractor, hardTtl, hardTtl, unit, refreshMode);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit) {
		this(request, extractor, hardTtl, softTtl, unit, RefreshMode.REQUEST_SYNC);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit, RefreshMode refreshMode) {
		super(request, hardTtl, softTtl, unit, refreshMode);

		if (extractor == null) {
			throw new IllegalArgumentException("null extractor");
		}
		this.extractor = extractor;

		this.resultType = null;
	}

	public CachingExtractorRequest(SenderRequest request, Class<T> resultType, long hardTtl, long softTtl, TimeUnit unit,
			RefreshMode refreshMode) {
		super(request, hardTtl, softTtl, unit, refreshMode);

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
