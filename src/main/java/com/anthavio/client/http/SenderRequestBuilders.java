package com.anthavio.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import com.anthavio.client.http.HttpSender.Multival;
import com.anthavio.client.http.SenderBodyRequest.FakeStream;
import com.anthavio.client.http.SenderBodyRequest.FakeStream.FakeType;
import com.anthavio.client.http.inout.ResponseBodyExtractor;
import com.anthavio.client.http.inout.ResponseBodyExtractor.ExtractedBodyResponse;

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
		 * Sets Accept Header for Content negotiation
		 */
		public X accept(String type) {
			headers.add("Accept", type);
			return getX();
		}

		/**
		 * Add Request Header
		 */
		public X header(String name, Object value) {
			headers.add(name, SenderRequest.toString(value));
			return getX();
		}

		/**
		 * Add Request Parameter
		 */
		public X param(String name, Object value) {
			parameters.add(name, SenderRequest.toString(value));
			return getX();
		}

		protected abstract X getX(); //Generic trick

		public abstract SenderRequest build();

		/**
		 * Execute request and return response
		 */
		public SenderResponse execute() throws IOException {
			SenderRequest request = build();
			return httpSender.execute(request);
		}

		/**
		 * Execute request and extract response
		 */
		public <T extends Serializable> ExtractedBodyResponse<T> extract(Class<T> clazz) throws IOException {
			SenderRequest request = build();
			return httpSender.extract(request, clazz);
		}

		/**
		 * Execute request and extract response
		 */
		public <T extends Serializable> ExtractedBodyResponse<T> extract(ResponseBodyExtractor<T> extractor)
				throws IOException {
			SenderRequest request = build();
			return httpSender.extract(request, extractor);
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

		protected boolean streaming;

		/**
		 * Set String as request body (entity)
		 */
		public SenderRequestBuilder<X> body(String body, String contentType) {
			this.bodyStream = new FakeStream(FakeType.STRING, body);
			this.contentType = contentType;
			return this;
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 */
		public X body(Object body, String contentType) throws IOException {
			body(body, contentType, false);
			return getX();
		}

		/**
		 * Set Object as request body (entity)
		 * Object will be marshalled/serialized to String
		 */
		public X body(Object body, String contentType, boolean streaming) throws IOException {
			this.bodyStream = new FakeStream(FakeType.OBJECT, body, streaming);
			this.contentType = contentType;
			this.streaming = streaming;
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
