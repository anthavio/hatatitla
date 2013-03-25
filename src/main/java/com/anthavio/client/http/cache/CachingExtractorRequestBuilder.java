package com.anthavio.client.http.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import com.anthavio.client.http.SenderRequest;
import com.anthavio.client.http.cache.CachingRequest.RefreshMode;
import com.anthavio.client.http.inout.ResponseBodyExtractor;

/**
 * Builder class for CachingExtractorRequest
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequestBuilder {

	private CachingExtractor cextractor;

	private SenderRequest request;

	private long hardTtl = 10000;

	private long softTtl = 5000;

	private RefreshMode refreshMode = RefreshMode.DURING_REQUEST;

	public CachingExtractorRequestBuilder(CachingExtractor cextractor, SenderRequest request) {
		if (cextractor == null) {
			throw new IllegalArgumentException("CachingExtractor is null");
		}
		this.cextractor = cextractor;

		if (request == null) {
			throw new IllegalArgumentException("SenderRequest is null");
		}
		this.request = request;
	}

	public CachingExtractorRequestBuilder hardTtl(long amount, TimeUnit unit) {
		this.hardTtl = unit.toMillis(amount);
		return this;
	}

	public CachingExtractorRequestBuilder softTtl(long amount, TimeUnit unit) {
		this.softTtl = unit.toMillis(amount);
		return this;
	}

	//this is not a best name for this method
	public <T extends Serializable> CachingExtractorRequest<T> build(ResponseBodyExtractor<T> extractor) {
		if (extractor == null) {
			throw new IllegalArgumentException("response extractor is null");
		}
		return new CachingExtractorRequest<T>(request, extractor, hardTtl, softTtl, TimeUnit.MILLISECONDS, refreshMode);
	}

	//this is not a best name for this method
	public <T extends Serializable> CachingExtractorRequest<T> build(Class<T> resultType) {
		if (resultType == null) {
			throw new IllegalArgumentException("response type is null");
		}
		return new CachingExtractorRequest<T>(request, resultType, hardTtl, softTtl, TimeUnit.MILLISECONDS, refreshMode);
	}

	public <T extends Serializable> T extract(Class<T> resultType) throws IOException {
		CachingExtractorRequest<T> build = build(resultType);
		return cextractor.extract(build);
	}

	public <T extends Serializable> T extract(ResponseBodyExtractor<T> extractor) throws IOException {
		CachingExtractorRequest<T> build = build(extractor);
		return cextractor.extract(build);
	}

}