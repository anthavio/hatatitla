package com.anthavio.httl.cache;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import com.anthavio.httl.SenderBodyRequest;
import com.anthavio.httl.SenderBodyRequest.FakeStream;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderRequest.Method;
import com.anthavio.httl.SenderRequestBuilders.AbstractRequestBuilder;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * Fluent builders for CachingSender Requests
 * 
 * @author martin.vanek
 *
 */
public class CachingRequestBuilders {

	/**
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class CachingRequestBuilder<X extends CachingRequestBuilder<?>> extends
			AbstractRequestBuilder<X> {

		protected final CachingSender sender;

		private long cacheAmount;

		private TimeUnit cacheUnit;

		public CachingRequestBuilder(CachingSender sender, Method method, String path) {
			super(sender.getSender(), method, path);
			this.sender = sender;
		}

		/**
		 * Sets static caching values - How long to keep response in cache
		 */
		public X cache(long amount, TimeUnit unit) {
			if (amount <= 0) {
				throw new IllegalArgumentException("Caching amount must be > 0");
			}
			this.cacheAmount = amount;

			if (unit == null) {
				throw new IllegalArgumentException("Caching unit is null");
			}
			this.cacheUnit = unit;

			return getX();
		}

		/**
		 * Execute Request and return raw unprocessed Response.
		 * Response is left open and caller is responsibe for closing.
		 */
		public SenderResponse execute() {
			SenderRequest request = build();
			return sender.execute(request);
		}

		/**
		 * Execute Request and extract Response. Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(Class<T> clazz) {
			SenderRequest request = build();
			if (cacheAmount > 0) {
				return sender.extract(new CachingRequest(request, cacheAmount, cacheUnit), clazz);
			} else {
				return sender.extract(request, clazz);
			}
		}

		/**
		 * Execute request and extract response. Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			if (cacheAmount > 0) {
				return sender.extract(new CachingRequest(request, cacheAmount, cacheUnit), extractor);
			} else {
				return sender.extract(request, extractor);
			}
		}

	}

	public static class CachingBodylessRequestBuilder extends CachingRequestBuilder<CachingBodylessRequestBuilder> {

		public CachingBodylessRequestBuilder(CachingSender httpSender, Method method, String path) {
			super(httpSender, method, path);
		}

		@Override
		protected CachingBodylessRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Base builder for requests with body
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingBodyRequestBuilder extends CachingRequestBuilder<CachingBodyRequestBuilder> {

		public CachingBodyRequestBuilder(CachingSender httpSender, Method method, String path) {
			super(httpSender, method, path);
		}

		protected String contentType;

		protected InputStream bodyStream;

		/**
		 * Set String as request body (entity)
		 */
		public CachingBodyRequestBuilder body(String body, String contentType) {
			this.bodyStream = new FakeStream(body);
			this.contentType = contentType;
			return getX();
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 */
		public CachingBodyRequestBuilder body(Object body, String contentType) {
			body(body, contentType, true);
			return getX();
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 * 
		 * @param streaming write directly into output stream or create interim String / byte[]
		 */
		public CachingBodyRequestBuilder body(Object body, String contentType, boolean streaming) {
			this.bodyStream = new FakeStream(body, streaming);
			this.contentType = contentType;
			return getX();
		}

		/**
		 * Set InputStream as request body (entity)
		 */
		public CachingBodyRequestBuilder body(InputStream stream, String contentType) {
			this.bodyStream = stream;
			this.contentType = contentType;
			return getX();
		}

		@Override
		public SenderBodyRequest build() {
			SenderBodyRequest request = new SenderBodyRequest(sender.getSender(), method, urlPath, parameters, headers);
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
		}

		@Override
		protected CachingBodyRequestBuilder getX() {
			return this;
		}
	}

}
