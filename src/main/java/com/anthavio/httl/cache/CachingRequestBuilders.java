package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.util.Cutils;

/**
 * Fluent builders for CachingSender Requests
 * 
 * @author martin.vanek
 *
 */
public class CachingRequestBuilders {

	/**
	 * Base builder for existing SenderRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class ExistingRequestBuilder<X extends ExistingRequestBuilder<?>> {

		protected final SenderRequest request;

		protected long hardTtl;

		protected long softTtl;

		protected RefreshMode refreshMode = RefreshMode.REQUEST_SYNC;

		protected String customCacheKey;

		public ExistingRequestBuilder(SenderRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("null request");
			}
			this.request = request;
		}

		/**
		 * Sets hard TTL - How long to keep resource in cache
		 */
		public X hardTTL(long ttl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.hardTtl = unit.toSeconds(ttl);

			if (this.hardTtl <= 1) {
				throw new IllegalArgumentException("Hard TTL must be at least 1 second");
			}

			if (softTtl == 0) {
				softTtl = hardTtl;
			}

			return getX();
		}

		/**
		 * Sets soft TTL - Interval between resource freshness checks
		 */
		public X softTTL(long ttl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}

			this.softTtl = unit.toSeconds(ttl);

			if (this.softTtl <= 1) {
				throw new IllegalArgumentException("Soft TTL must be at least 1 second");
			}

			if (hardTtl == 0) {
				hardTtl = softTtl;
			}

			return getX();
		}

		/**
		 * Sets both hard and soft TTL to same value
		 */
		public X ttl(long ttl, TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}
			this.hardTtl = unit.toSeconds(ttl);
			if (this.hardTtl <= 1) {
				throw new IllegalArgumentException("TTL must be at least 1 second");
			}
			this.softTtl = hardTtl;

			return getX();
		}

		/**
		 * Sets both hard and soft TTL
		 */
		public X ttl(long hardTtl, long softTtl, TimeUnit unit) {
			if (softTtl > hardTtl) {
				throw new IllegalArgumentException("Hard TTL must be greater then Soft TTL");
			}
			softTTL(softTtl, unit);
			hardTTL(hardTtl, unit);
			return getX();
		}

		/**
		 * Sets mode of refreshing
		 */
		public X refresh(RefreshMode mode) {
			if (mode == null) {
				throw new IllegalArgumentException("null mode");
			}
			this.refreshMode = mode;
			return getX();
		}

		/**
		 * Sets custom caching key to be used intead of default key derived from url
		 */
		public X customCacheKey(String key) {
			if (Cutils.isBlank(key)) {
				throw new IllegalArgumentException("Blank key");
			}
			this.customCacheKey = key;
			return getX();
		}

		/**
		 * hack 
		 */
		protected abstract X getX();

	}

	/**
	 * Fluent builder for CachingRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingRequestBuilder extends ExistingRequestBuilder<CachingRequestBuilder> {

		private final CachingSender csender;

		public CachingRequestBuilder(CachingSender csender, SenderRequest request) {
			super(request);

			if (csender == null) {
				throw new IllegalArgumentException("null sender");
			}
			this.csender = csender;
		}

		public final CachingRequest build() {
			return new CachingRequest(request, hardTtl, softTtl, TimeUnit.SECONDS, refreshMode, customCacheKey);
		}

		/**
		 * Execute Request and return raw unprocessed Response.
		 * Response is left open and caller is responsibe for closing.
		 */
		public SenderResponse execute() {
			if (hardTtl != 0) {
				return csender.execute(build());
			} else {
				return csender.execute(request);
			}
		}

		/**
		 * Execute Request and extract Response. Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(Class<T> clazz) {
			if (hardTtl != 0) {
				return csender.extract(build(), clazz);
			} else {
				return csender.extract(request, clazz);
			}
		}

		/**
		 * Execute request and extract response. Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor) {
			if (hardTtl != 0) {
				return csender.extract(build(), extractor);
			} else {
				return csender.extract(request, extractor);
			}
		}

		@Override
		protected CachingRequestBuilder getX() {
			return this;
		}

	}

	/**
	 * Fluent builder for CachingExtractorRequest
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingExtractorRequestBuilder extends ExistingRequestBuilder<CachingExtractorRequestBuilder> {

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
			return new CachingExtractorRequest<T>(request, extractor, hardTtl, softTtl, TimeUnit.SECONDS, refreshMode,
					customCacheKey);
		}

		/**
		 * Finish fluent builder flow and return CachingExtractorRequest
		 */
		public <T> CachingExtractorRequest<T> build(Class<T> resultType) {
			if (resultType == null) {
				throw new IllegalArgumentException("response type is null");
			}
			return new CachingExtractorRequest<T>(request, resultType, hardTtl, softTtl, TimeUnit.SECONDS, refreshMode,
					customCacheKey);
		}

		/**
		 * Go and extract!
		 */
		public <T> T extract(Class<T> resultType) {
			CachingExtractorRequest<T> build = build(resultType);
			return cextractor.extract(build);
		}

		/**
		 * Go and extract!
		 */
		public <T> T extract(ResponseBodyExtractor<T> extractor) {
			CachingExtractorRequest<T> build = build(extractor);
			return cextractor.extract(build);
		}

		@Override
		protected CachingExtractorRequestBuilder getX() {
			return this;
		}
	}
}
