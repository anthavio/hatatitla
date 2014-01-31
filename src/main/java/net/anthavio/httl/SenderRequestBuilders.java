package net.anthavio.httl;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Future;

import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.SenderBodyRequest.FakeStream;
import net.anthavio.httl.SenderRequest.Method;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import net.anthavio.httl.inout.ResponseBodyHandler;
import net.anthavio.httl.inout.ResponseHandler;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.GenericType;

/**
 * Equivalent of Jersey/JAX-RS-2.0 RequestBuilder
 * 
 * @author martin.vanek
 *
 */
public class SenderRequestBuilders {

	/**
	 * Base class for all request fluent builders
	 * 
	 * @author martin.vanek
	 *
	 */
	public abstract static class AbstractRequestBuilder<X extends AbstractRequestBuilder<?>> {

		protected final HttpSender httpSender;

		protected final Method method;

		protected final String urlPath;

		protected Multival headers = new Multival();

		protected Multival parameters = new Multival();

		public AbstractRequestBuilder(HttpSender httpSender, Method method, String urlPath) {
			if (httpSender == null) {
				throw new IllegalArgumentException("sender is null");
			}
			this.httpSender = httpSender;

			if (method == null) {
				throw new IllegalArgumentException("method is null");
			}
			this.method = method;

			if (Cutils.isEmpty(urlPath)) {
				throw new IllegalArgumentException("urlPath is blank");
			}
			this.urlPath = urlPath;
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

	}

	/**
	 * Base for HttpSender's fluent builders
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class SenderRequestBuilder<X extends SenderRequestBuilder<?>> extends
			AbstractRequestBuilder<X> {

		public SenderRequestBuilder(HttpSender httpSender, Method method, String urlPath) {
			super(httpSender, method, urlPath);
		}

		public SenderRequest build() {
			return new SenderRequest(httpSender, method, urlPath, parameters, headers);
		}

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
		public <T> ExtractedBodyResponse<T> extract(Class<T> clazz) {
			SenderRequest request = build();
			return httpSender.extract(request, clazz);
		}

		/**
		 * Execute Request and extract Response.
		 * Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(GenericType<T> typeReference) {
			SenderRequest request = build();
			return httpSender.extract(request, typeReference);
		}

		/**
		 * Execute request and extract response.
		 * Response is closed automaticaly.
		 */
		public <T> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return httpSender.extract(request, extractor);
		}

		public Future<SenderResponse> start() {
			SenderRequest request = build();
			return httpSender.start(request);
		}

		public <T> Future<ExtractedBodyResponse<T>> start(ResponseBodyExtractor<T> extractor) {
			SenderRequest request = build();
			return httpSender.start(request, extractor);
		}

		public <T> Future<ExtractedBodyResponse<T>> start(Class<T> resultType) {
			SenderRequest request = build();
			return httpSender.start(request, resultType);
		}

		public void start(ResponseHandler handler) {
			SenderRequest request = build();
			httpSender.start(request, handler);
		}

		public <T> void start(ResponseBodyHandler<T> handler) {
			SenderRequest request = build();
			httpSender.start(request, handler);
		}
	}

	/**
	 * GET, HEAD, DELETE, OPTIONS
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderNobodyRequestBuilder extends SenderRequestBuilder<SenderNobodyRequestBuilder> {

		public SenderNobodyRequestBuilder(HttpSender httpSender, Method method, String urlPath) {
			super(httpSender, method, urlPath);
		}

		@Override
		protected SenderNobodyRequestBuilder getX() {
			return this;
		}

	}

	/**
	 * PUT, POST
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class SenderBodyRequestBuilder<X extends SenderRequestBuilder<?>> extends
			SenderRequestBuilder<X> {

		public SenderBodyRequestBuilder(HttpSender httpSender, Method method, String path) {
			super(httpSender, method, path);
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

		@Override
		public SenderBodyRequest build() {
			SenderBodyRequest request = (SenderBodyRequest) super.build();
			if (bodyStream != null) {
				request.setBody(bodyStream, contentType);
			}
			return request;
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
			super(httpSender, Method.GET, path);
		}

		@Override
		public GetRequest build() {
			return new GetRequest(httpSender, urlPath, parameters, headers);
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
			super(httpSender, Method.DELETE, path);
		}

		@Override
		public DeleteRequest build() {
			return new DeleteRequest(httpSender, urlPath, parameters, headers);
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
			super(httpSender, Method.HEAD, path);
		}

		@Override
		public HeadRequest build() {
			return new HeadRequest(httpSender, urlPath, parameters, headers);
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
			super(httpSender, Method.OPTIONS, path);
		}

		@Override
		public OptionsRequest build() {
			return new OptionsRequest(httpSender, urlPath, parameters, headers);
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
			super(httpSender, Method.POST, path);
		}

		@Override
		public PostRequest build() {
			PostRequest request = new PostRequest(httpSender, urlPath, parameters, headers);
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
	public static class SenderPutRequestBuilder extends SenderBodyRequestBuilder {

		public SenderPutRequestBuilder(HttpSender httpSender, String path) {
			super(httpSender, Method.PUT, path);
		}

		@Override
		public PutRequest build() {
			PutRequest request = new PutRequest(httpSender, urlPath, parameters, headers);
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
