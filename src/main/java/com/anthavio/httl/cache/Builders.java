package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Builders.BaseCacheRequestBuilder;
import com.anthavio.cache.CacheEntry;
import com.anthavio.cache.CacheEntryLoader;
import com.anthavio.cache.CacheLoadRequest;
import com.anthavio.cache.CachingSettings;
import com.anthavio.cache.LoadingSettings;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * Fluent builders for CachingSender Requests
 * 
 * @author martin.vanek
 *
 */
public class Builders {

	/**
	 * Base builder for existing SenderRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class BaseRequestBuilder<S extends BaseRequestBuilder<?>> extends BaseCacheRequestBuilder<S> {

		protected final SenderRequest request;

		public BaseRequestBuilder(SenderRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("null request");
			}
			this.request = request;
		}

	}

	/**
	 * Fluent builder for CachingRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingRequestBuilder extends BaseRequestBuilder<CachingRequestBuilder> {

		private final CachingSender csender;

		private boolean missingLoadAsync;

		private boolean expiredLoadAsync;

		public CachingRequestBuilder(CachingSender csender, SenderRequest request) {
			super(request);

			if (csender == null) {
				throw new IllegalArgumentException("null sender");
			}
			this.csender = csender;
		}

		public final CachingSenderRequest build() {
			return new CachingSenderRequest(request, missingLoadAsync, expiredLoadAsync, hardTtl, softTtl, TimeUnit.SECONDS,
					cacheKey);
		}

		/**
		 * Execute Request and return raw unprocessed Response.
		 * Response is left open and caller is responsibe for closing.
		 */
		public SenderResponse execute() {
			if (hardTtl != 0) {
				return csender.execute(build()).getValue();
			} else {
				return csender.execute(request);
			}
		}

		/**
		 * Execute Request and extract Response. Response is closed automaticaly.
		 */
		public <V> ExtractedBodyResponse<V> extract(Class<V> clazz) {
			if (hardTtl != 0) {
				return csender.extract(build(), clazz);
			} else {
				return csender.extract(request, clazz);
			}
		}

		/**
		 * Execute request and extract response. Response is closed automaticaly.
		 */
		public <V> ExtractedBodyResponse<V> extract(ResponseBodyExtractor<V> extractor) {
			if (hardTtl != 0) {
				return csender.extract(build(), extractor);
			} else {
				return csender.extract(request, extractor);
			}
		}

		@Override
		protected CachingRequestBuilder getSelf() {
			return this;
		}

	}

	/**
	 * Fluent builder for CachingExtractorRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingExtractorRequestBuilder extends BaseRequestBuilder<CachingExtractorRequestBuilder> {

		private CachingExtractor cextractor;

		public CachingExtractorRequestBuilder(CachingExtractor cextractor, SenderRequest request) {
			super(request);
			if (cextractor == null) {
				throw new IllegalArgumentException("CachingExtractor is null");
			}
			this.cextractor = cextractor;

		}

		/**
		 * Finish fluent builder flow and return CachingExtractorRequest
		 */
		public <T> CachingExtractorRequest<T> build(ResponseBodyExtractor<T> extractor) {
			if (extractor == null) {
				throw new IllegalArgumentException("response extractor is null");
			}
			return new CachingExtractorRequest<T>(request, extractor, hardTtl, softTtl, TimeUnit.SECONDS, mode, cacheKey);
		}

		/**
		 * Finish fluent builder flow and return CachingExtractorRequest
		 */
		public <T> CachingExtractorRequest<T> build(Class<T> resultType) {
			if (resultType == null) {
				throw new IllegalArgumentException("response type is null");
			}
			return new CachingExtractorRequest<T>(request, resultType, hardTtl, softTtl, TimeUnit.SECONDS, mode, cacheKey);
		}

		/**
		 * Go and extract!
		 */
		public <T> T extract(Class<T> resultType) {
			CachingExtractorRequest<T> build = build(resultType);
			return (T) cextractor.extract(build).getValue();
		}

		/**
		 * Go and extract!
		 */
		public <T> T extract(ResponseBodyExtractor<T> extractor) {
			CachingExtractorRequest<T> build = build(extractor);
			return (T) cextractor.extract(build).getValue();
		}

		@Override
		protected CachingExtractorRequestBuilder getSelf() {
			return this;
		}
	}
}
