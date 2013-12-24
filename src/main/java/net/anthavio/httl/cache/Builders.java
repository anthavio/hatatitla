package net.anthavio.httl.cache;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.Builders.BaseCacheRequestBuilder;
import net.anthavio.cache.ConfiguredCacheLoader.ExpiredFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.MissingFailedRecipe;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

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

		private boolean missingLoadAsync = false;

		private MissingFailedRecipe misingFailedRecipe;

		private boolean expiredLoadAsync = false;

		private ExpiredFailedRecipe expiredFailedRecipe;

		public CachingRequestBuilder(CachingSender csender, SenderRequest request) {
			super(request);

			if (csender == null) {
				throw new IllegalArgumentException("null sender");
			}
			this.csender = csender;
		}

		public BaseRequestBuilder<CachingRequestBuilder> async(boolean missing, boolean expired) {
			this.missingLoadAsync = missing;
			this.expiredLoadAsync = expired;
			return getSelf();
		}

		public BaseRequestBuilder<CachingRequestBuilder> missing(boolean asynchronous, MissingFailedRecipe onFailure) {
			this.missingLoadAsync = asynchronous;
			this.misingFailedRecipe = onFailure;
			return getSelf();
		}

		public BaseRequestBuilder<CachingRequestBuilder> expired(boolean asynchronous, ExpiredFailedRecipe onFailure) {
			this.expiredLoadAsync = asynchronous;
			this.expiredFailedRecipe = onFailure;
			return getSelf();
		}

		public final CachingSenderRequest build() {
			if (misingFailedRecipe == null) {
				if (missingLoadAsync) {
					misingFailedRecipe = MissingFailedRecipe.ASYN_STRICT;
				} else {
					misingFailedRecipe = MissingFailedRecipe.SYNC_STRICT;
				}
			}
			if (expiredFailedRecipe == null) {
				if (expiredLoadAsync) {
					expiredFailedRecipe = ExpiredFailedRecipe.ASYN_STRICT;
				} else {
					expiredFailedRecipe = ExpiredFailedRecipe.SYNC_STRICT;
				}
			}

			return new CachingSenderRequest(request, missingLoadAsync, misingFailedRecipe, expiredLoadAsync,
					expiredFailedRecipe, hardTtl, softTtl, TimeUnit.SECONDS, cacheKey);
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
	public static class ExtractingRequestBuilder extends BaseRequestBuilder<ExtractingRequestBuilder> {

		private CachingExtractor cextractor;

		private boolean missingLoadAsync = false;

		private MissingFailedRecipe misingFailedRecipe;

		private boolean expiredLoadAsync = false;

		private ExpiredFailedRecipe expiredFailedRecipe;

		public ExtractingRequestBuilder(CachingExtractor cextractor, SenderRequest request) {
			super(request);
			if (cextractor == null) {
				throw new IllegalArgumentException("CachingExtractor is null");
			}
			this.cextractor = cextractor;

		}

		@Override
		protected ExtractingRequestBuilder getSelf() {
			return this;
		}

		public ExtractingRequestBuilder async(boolean missing, boolean expired) {
			this.missingLoadAsync = missing;
			this.expiredLoadAsync = expired;
			return getSelf();
		}

		public ExtractingRequestBuilder missing(boolean asynchronous, MissingFailedRecipe onFailure) {
			this.missingLoadAsync = asynchronous;
			this.misingFailedRecipe = onFailure;
			return getSelf();
		}

		public ExtractingRequestBuilder expired(boolean asynchronous, ExpiredFailedRecipe onFailure) {
			this.expiredLoadAsync = asynchronous;
			this.expiredFailedRecipe = onFailure;
			return getSelf();
		}

		/**
		 * Finish fluent builder flow and return CachingExtractorRequest
		 */
		public <T extends Serializable> CachingExtractorRequest<T> build(ResponseBodyExtractor<T> extractor) {
			if (extractor == null) {
				throw new IllegalArgumentException("response extractor is null");
			}

			if (misingFailedRecipe == null) {
				if (missingLoadAsync) {
					misingFailedRecipe = MissingFailedRecipe.ASYN_STRICT;
				} else {
					misingFailedRecipe = MissingFailedRecipe.SYNC_STRICT;
				}
			}
			if (expiredFailedRecipe == null) {
				if (expiredLoadAsync) {
					expiredFailedRecipe = ExpiredFailedRecipe.ASYN_STRICT;
				} else {
					expiredFailedRecipe = ExpiredFailedRecipe.SYNC_STRICT;
				}
			}

			return new CachingExtractorRequest<T>(extractor, request, missingLoadAsync, misingFailedRecipe, expiredLoadAsync,
					expiredFailedRecipe, hardTtl, softTtl, TimeUnit.SECONDS, cacheKey);

		}

		/**
		 * Finish fluent builder flow and return CachingExtractorRequest
		 */
		public <T extends Serializable> CachingExtractorRequest<T> build(Class<T> resultType) {
			if (resultType == null) {
				throw new IllegalArgumentException("response type is null");
			}
			if (misingFailedRecipe == null) {
				if (missingLoadAsync) {
					misingFailedRecipe = MissingFailedRecipe.ASYN_STRICT;
				} else {
					misingFailedRecipe = MissingFailedRecipe.SYNC_STRICT;
				}
			}
			if (expiredFailedRecipe == null) {
				if (expiredLoadAsync) {
					expiredFailedRecipe = ExpiredFailedRecipe.ASYN_STRICT;
				} else {
					expiredFailedRecipe = ExpiredFailedRecipe.SYNC_STRICT;
				}
			}

			return new CachingExtractorRequest<T>(resultType, request, missingLoadAsync, misingFailedRecipe,
					expiredLoadAsync, expiredFailedRecipe, hardTtl, softTtl, TimeUnit.SECONDS, cacheKey);
		}

		/**
		 * Go and extract!
		 
		public <T extends Serializable> T extract(Class<T> resultType) {
			CachingExtractorRequest<T> build = build(resultType);
			return (T) cextractor.extract(build).getValue();
		}
		 */

		/**
		 * Go and extract!
		 
		public <T extends Serializable> T extract(ResponseBodyExtractor<T> extractor) {
			CachingExtractorRequest<T> build = build(extractor);
			return (T) cextractor.extract(build).getValue();
		}
		*/
	}

}
