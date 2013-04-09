package com.anthavio.httl.cache;

import java.io.InputStream;
import java.util.Map;

import com.anthavio.httl.Cutils;
import com.anthavio.httl.DeleteRequest;
import com.anthavio.httl.GetRequest;
import com.anthavio.httl.HeadRequest;
import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.OptionsRequest;
import com.anthavio.httl.PostRequest;
import com.anthavio.httl.PutRequest;
import com.anthavio.httl.SenderBodyRequest.FakeStream;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.inout.ResponseHandler;

/**
 * Fluent builders for CachingSender Requests
 * 
 * XXX Almost exact copy of SenderRequestBuilders. Can't we wrap SenderRequestBuilders at least?
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
	public static abstract class CachingRequestBuilder<X extends CachingRequestBuilder<?>> {

		protected final CachingSender sender;

		protected final String path;

		protected Multival headers = new Multival();

		protected Multival parameters = new Multival();

		public CachingRequestBuilder(CachingSender httpSender, String path) {
			if (httpSender == null) {
				throw new IllegalArgumentException("sender is null");
			}
			this.sender = httpSender;

			if (Cutils.isEmpty(path)) {
				throw new IllegalArgumentException("path is blank");
			}
			this.path = path;
		}

		/**
		 * Sets Accept Header for Content-Type negotiation
		 */
		public X accept(String type) {
			headers.set("Accept", type);
			return getX();
		}

		/**
		 * Add Request Header
		 */
		public X header(String name, Object value) {
			headers.add(name, value);
			return getX();
		}

		/**
		 * Add Request Parameter
		 */
		public X param(String name, Object value) {
			parameters.add(name, value);
			return getX();
		}

		/**
		 * Add Matrix Parameter
		 */
		public X matrix(String name, Object value) {
			if (name.charAt(0) != ';') {
				name = ";" + name;
			}
			parameters.add(name, value);
			return getX();
		}

		/**
		 * Sets parameters (replacing any existing)
		 */
		public X parameters(Map<String, ?> parameters) {
			this.parameters = new Multival(parameters);
			return getX();
		}

		protected abstract X getX(); //Generic trick

		/**
		 * End of the fluent builder chain.
		 */
		public abstract SenderRequest build();

		/**
		 * Execute Request and return raw unprocessed Response.
		 * Response is lefty open and caller is responsibe for closing.
		 */
		public SenderResponse execute() {
			SenderRequest request = build();
			return sender.execute(request);
		}

		/**
		 * Execute Request and use ResponseHandler parameter to process Response.
		 * Response is closed automaticaly.
		 */
		public void execute(ResponseHandler handler) {
			SenderRequest request = build();
			sender.execute(request, handler);
		}

		/**
		 * Execute Request and extract Response.
		 * Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(Class<T> clazz) {
			SenderRequest request = build();
			return sender.extract(request, clazz);
		}

		/**
		 * Execute request and extract response.
		 * Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return sender.extract(request, extractor);
		}

		/*
		public Future<SenderResponse> start() {
			SenderRequest request = build();
			return sender.start(request);
		}

		public <T> Future<ExtractedBodyResponse<T>> start(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return sender.start(request, extractor);
		}

		public <T> Future<ExtractedBodyResponse<T>> start(Class<T> resultType) {
			SenderRequest request = build();
			return sender.start(request, resultType);
		}

		public void start(ResponseHandler handler) {
			SenderRequest request = build();
			sender.start(request, handler);
		}

		public <T> void start(ResponseBodyHandler<T> handler) {
			SenderRequest request = build();
			sender.start(request, handler);
		}
		*/
	}

	/**
	 * Base builder for requests with body
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class CachingBodyRequestBuilder<X extends CachingRequestBuilder<?>> extends
			CachingRequestBuilder<X> {

		public CachingBodyRequestBuilder(CachingSender httpSender, String path) {
			super(httpSender, path);
		}

		protected String contentType;

		protected InputStream bodyStream;

		/**
		 * Set String as request body (entity)
		 */
		public X body(String body, String contentType) {
			this.bodyStream = new FakeStream(body);
			this.contentType = contentType;
			return getX();
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 */
		public X body(Object body, String contentType) {
			body(body, contentType, true);
			return getX();
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 * 
		 * @param streaming write directly into output stream or create interim String / byte[]
		 */
		public X body(Object body, String contentType, boolean streaming) {
			this.bodyStream = new FakeStream(body, streaming);
			this.contentType = contentType;
			return getX();
		}

		/**
		 * Set InputStream as request body (entity)
		 */
		public X body(InputStream stream, String contentType) {
			this.bodyStream = stream;
			this.contentType = contentType;
			return getX();
		}
	}

	/**
	 * Builder for GET request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingGetRequestBuilder extends CachingRequestBuilder<CachingGetRequestBuilder> {

		public CachingGetRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public GetRequest build() {
			return new GetRequest(sender.getSender(), path, parameters, headers);
		}

		@Override
		protected CachingGetRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for DELETE request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingDeleteRequestBuilder extends CachingRequestBuilder<CachingDeleteRequestBuilder> {

		public CachingDeleteRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public DeleteRequest build() {
			return new DeleteRequest(sender.getSender(), path, parameters, headers);
		}

		@Override
		protected CachingDeleteRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for HEAD request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingHeadRequestBuilder extends CachingRequestBuilder<CachingHeadRequestBuilder> {

		public CachingHeadRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public HeadRequest build() {
			return new HeadRequest(sender.getSender(), path, parameters, headers);
		}

		@Override
		protected CachingHeadRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for OPTIONS request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingOptionsRequestBuilder extends CachingRequestBuilder<CachingOptionsRequestBuilder> {

		public CachingOptionsRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public OptionsRequest build() {
			return new OptionsRequest(sender.getSender(), path, parameters, headers);
		}

		@Override
		protected CachingOptionsRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for POST request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingPostRequestBuilder extends CachingBodyRequestBuilder<CachingPostRequestBuilder> {

		public CachingPostRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public PostRequest build() {
			PostRequest request = new PostRequest(sender.getSender(), path, parameters, headers);
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
		}

		@Override
		protected CachingPostRequestBuilder getX() {
			return this;
		}

	}

	/**
	 * Builder for PUT request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class CachingPutRequestBuilder extends CachingBodyRequestBuilder<CachingPutRequestBuilder> {

		public CachingPutRequestBuilder(CachingSender sender, String path) {
			super(sender, path);
		}

		@Override
		public PutRequest build() {
			PutRequest request = new PutRequest(sender.getSender(), path, parameters, headers);
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
		}

		@Override
		protected CachingPutRequestBuilder getX() {
			return this;
		}
	}

}
