package com.anthavio.hatatitla;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Future;

import com.anthavio.hatatitla.HttpSender.Multival;
import com.anthavio.hatatitla.SenderBodyRequest.FakeStream;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.hatatitla.inout.ResponseBodyHandler;
import com.anthavio.hatatitla.inout.ResponseHandler;

/**
 * Equivalent of Jersey/JAX-RS-2.0 RequestBuilder
 * 
 * @author martin.vanek
 *
 */
public class SenderRequestBuilders {

	/**
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class SenderRequestBuilder<X extends SenderRequestBuilder<?>> {

		protected final HttpSender httpSender;

		protected final String path;

		protected Multival headers = new Multival();

		protected Multival parameters = new Multival();

		public SenderRequestBuilder(HttpSender httpSender, String path) {
			if (httpSender == null) {
				throw new IllegalArgumentException("sender is null");
			}
			this.httpSender = httpSender;

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
			return httpSender.execute(request);
		}

		/**
		 * Execute Request and use ResponseHandler parameter to process Response.
		 * Response is closed automaticaly.
		 */
		public void execute(ResponseHandler handler) {
			SenderRequest request = build();
			httpSender.execute(request, handler);
		}

		/**
		 * Execute Request and extract Response.
		 * Response is closed automaticaly.
		 */
		public <T extends Serializable> ExtractedBodyResponse<T> extract(Class<T> clazz) {
			SenderRequest request = build();
			return httpSender.extract(request, clazz);
		}

		/**
		 * Execute request and extract response.
		 * Response is closed automaticaly.
		 */
		public <T extends Serializable> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return httpSender.extract(request, extractor);
		}

		public Future<SenderResponse> start() {
			SenderRequest request = build();
			return httpSender.start(request);
		}

		public <T extends Serializable> Future<ExtractedBodyResponse<T>> start(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return httpSender.start(request, extractor);
		}

		public <T extends Serializable> Future<ExtractedBodyResponse<T>> start(Class<T> resultType) {
			SenderRequest request = build();
			return httpSender.start(request, resultType);
		}

		public void start(ResponseHandler handler) {
			SenderRequest request = build();
			httpSender.start(request, handler);
		}

		public <T extends Serializable> void start(ResponseBodyHandler<T> handler) {
			SenderRequest request = build();
			httpSender.start(request, handler);
		}
	}

	/**
	 * Base builder for requests with body
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class SenderBodyRequestBuilder<X extends SenderRequestBuilder<?>> extends
			SenderRequestBuilder<X> {

		public SenderBodyRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		protected String contentType;

		protected InputStream bodyStream;

		/**
		 * Set String as request body (entity)
		 */
		public SenderRequestBuilder<X> body(String body, String contentType) {
			this.bodyStream = new FakeStream(body);
			this.contentType = contentType;
			return this;
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
		public SenderRequestBuilder<X> body(InputStream stream, String contentType) {
			this.bodyStream = stream;
			this.contentType = contentType;
			return this;
		}
	}

	/**
	 * Builder for GET request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderGetRequestBuilder extends SenderRequestBuilder<SenderGetRequestBuilder> {

		public SenderGetRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public GetRequest build() {
			return new GetRequest(httpSender, path, parameters, headers);
		}

		@Override
		protected SenderGetRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for DELETE request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderDeleteRequestBuilder extends SenderRequestBuilder<SenderDeleteRequestBuilder> {

		public SenderDeleteRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public DeleteRequest build() {
			return new DeleteRequest(httpSender, path, parameters, headers);
		}

		@Override
		protected SenderDeleteRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for HEAD request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderHeadRequestBuilder extends SenderRequestBuilder<SenderHeadRequestBuilder> {

		public SenderHeadRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public HeadRequest build() {
			return new HeadRequest(httpSender, path, parameters, headers);
		}

		@Override
		protected SenderHeadRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for OPTIONS request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderOptionsRequestBuilder extends SenderRequestBuilder<SenderOptionsRequestBuilder> {

		public SenderOptionsRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public OptionsRequest build() {
			return new OptionsRequest(httpSender, path, parameters, headers);
		}

		@Override
		protected SenderOptionsRequestBuilder getX() {
			return this;
		}
	}

	/**
	 * Builder for POST request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderPostRequestBuilder extends SenderBodyRequestBuilder<SenderPostRequestBuilder> {

		public SenderPostRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public PostRequest build() {
			PostRequest request = new PostRequest(httpSender, path, parameters, headers);
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
		}

		@Override
		protected SenderPostRequestBuilder getX() {
			return this;
		}

	}

	/**
	 * Builder for PUT request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderPutRequestBuilder extends SenderBodyRequestBuilder<SenderPutRequestBuilder> {

		public SenderPutRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, path);
		}

		@Override
		public PutRequest build() {
			PutRequest request = new PutRequest(httpSender, path, parameters, headers);
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
		}

		@Override
		protected SenderPutRequestBuilder getX() {
			return this;
		}
	}

}
