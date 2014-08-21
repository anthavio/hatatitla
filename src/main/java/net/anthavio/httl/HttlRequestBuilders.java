package net.anthavio.httl;

import java.io.InputStream;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.GenericType;
import net.anthavio.httl.util.HttpDateUtil;
import net.anthavio.httl.util.ReaderInputStream;

/**
 * Equivalent of Jersey/JAX-RS-2.0 RequestBuilder
 * 
 * @author martin.vanek
 *
 */
public class HttlRequestBuilders {

	/**
	 * Base class for all request fluent builders
	 * 
	 * @author martin.vanek
	 *
	 */
	public abstract static class HttlRequestBuilder<X extends HttlRequestBuilder<?>> {

		protected final HttlSender sender;

		protected final Method method;

		protected final String urlPath;

		protected final Multival<String> headers;

		protected final Multival<String> parameters;

		protected final HttlParameterSetter paramSetter;

		protected Integer readTimeoutMillis;

		public HttlRequestBuilder(HttlSender sender, Method method, String urlPath) {
			if (sender == null) {
				throw new IllegalArgumentException("sender is null");
			}
			this.sender = sender;

			this.headers = new Multival<String>();
			this.parameters = new Multival<String>();

			if (method == null) {
				throw new IllegalArgumentException("method is null");
			}
			this.method = method;

			if (Cutils.isEmpty(urlPath)) {
				throw new IllegalArgumentException("url path is blank");
			}
			this.urlPath = urlPath;

			this.paramSetter = sender.getConfig().getParamSetter();
		}

		public HttlSender getSender() {
			return sender;
		}

		public Method getMethod() {
			return method;
		}

		public Multival<String> getHeaders() {
			return headers;
		}

		public Multival<String> getParameters() {
			return parameters;
		}

		public X timeout(int value, TimeUnit unit) {
			this.readTimeoutMillis = (int) unit.toMillis(value);
			return getX();
		}

		// headers section...

		/**
		 * Set Headers replacing all existing
		 */
		public X setHeaders(Map<String, ?> headers) {
			this.headers.clear();
			addHeaders(headers);
			return getX();
		}

		/**
		 * Add Headers
		 */
		public X addHeaders(Map<String, ?> headers) {
			Set<String> keySet = headers.keySet();
			for (String key : keySet) {
				Object object = headers.get(key);
				if (object instanceof Collection) {
					this.headers.add(key, (Collection<String>) object);
				} else {
					this.headers.add(key, String.valueOf(object));
				}
			}
			return getX();
		}

		/**
		 * Set Header with single value
		 * If Header already exists, old value(s) will be replaced
		 */
		public X setHeader(String name, String value) {
			this.headers.set(name, value);
			return getX();
		}

		public X setHeader(String name, Date value) {
			this.headers.set(name, HttpDateUtil.formatDate(value));
			return getX();
		}

		/**
		 * Set Header with multiple values
		 * If Header already exists, old value(s) will be replaced
		 */
		public X setHeader(String name, String... values) {
			this.headers.set(name, values);
			return getX();
		}

		public X setHeader(String name, Collection<String> values) {
			this.headers.set(name, values);
			return getX();
		}

		/**
		 * Formats the given date according to the RFC 1123 pattern.
		 */
		public X header(String name, Date value) {
			this.headers.add(name, HttpDateUtil.formatDate(value));
			return getX();
		}

		/**
		 * Add Header with multiple values
		 */
		public X header(String name, String... values) {
			this.headers.add(name, values);
			return getX();
		}

		public X header(String name, Collection<String> values) {
			this.headers.add(name, values);
			return getX();
		}

		/**
		 * Add Request Header
		 */
		public X header(String name, String value) {
			headers.add(name, value);
			return getX();
		}

		// parameters section...

		public X param(String name, int value) {
			paramSetter.handle(parameters, false, name, value);
			return getX();
		}

		public X param(String name, String value) {
			paramSetter.handle(parameters, false, name, value);
			return getX();
		}

		public X param(String name, Date value) {
			paramSetter.handle(parameters, false, name, value);
			return getX();
		}

		public X param(String name, Date value, String format) {
			if (value != null) {
				String formated = new SimpleDateFormat(format).format(value);
				paramSetter.handle(parameters, false, name, formated);
			} else {
				paramSetter.handle(parameters, false, name, null);
			}
			return getX();
		}

		/**
		 *  Set parameters frm Map
		 */
		public X params(Map<String, ?> map) {
			if (map != null) {
				Set<String> keySet = map.keySet();
				for (String name : keySet) {
					Object value = map.get(name);
					paramSetter.handle(parameters, false, name, value);
				}
			}
			return getX();
		}

		/**
		 * Set/Add parameter with single value.
		 */
		public X param(boolean reset, String name, Object value) {
			paramSetter.handle(parameters, reset, name, value);
			return getX();
		}

		/**
		 * Add parameter with single value.
		 */
		public X param(String name, Object value) {
			return param(false, name, value);
		}

		/**
		 * Add parameter with multiple values.
		 */
		public X param(String name, Collection<?> values) {
			return param(false, name, values);
		}

		public X param(boolean reset, String name, Collection<?> values) {
			paramSetter.handle(parameters, reset, name, values);
			return getX();
		}

		public X param(boolean reset, String name, Object... values) {
			paramSetter.handle(parameters, reset, name, values);
			return getX();
		}

		/**
		 * Add parameter with multiple values.
		 */
		public X param(String name, Object... values) {
			paramSetter.handle(parameters, false, name, values);
			return getX();
		}

		/**
		 * Add Matrix Url Parameter
		 * 
		 * Matrix - http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/mvc.html#mvc-ann-matrix-variables
		 */
		public X matrix(String name, Object value) {
			return matrix(false, name, value);
		}

		/**
		 * Matrix parameter is allways part of URL.
		 */
		public X matrix(boolean reset, String name, Object value) {
			if (name.charAt(0) != ';') {
				name = ";" + name;
			}
			paramSetter.handle(parameters, reset, name, value);
			return getX();
		}

		/**
		 * Path parameter is allways part of url
		 */
		public X pathParam(String name, Object value) {
			if (name.charAt(0) != '{') {
				name = "{" + name + "}";
			}
			if (urlPath.indexOf(name) == -1) {
				throw new IllegalArgumentException("URL path variable: " + name + " not found in: " + urlPath);
			}
			if (value == null) {
				throw new IllegalArgumentException("Null value for path parameter " + name + " is illegal");
			} else if (value instanceof Collection) {
				throw new IllegalArgumentException("Collection value for path parameter " + name + " is illegal");
			} else if (value.getClass().isArray()) {
				throw new IllegalArgumentException("Array value for path parameter " + name + " is illegal");
			} else {
				paramSetter.handle(parameters, true, name, value);
			}
			return getX();
		}

		/**
		 * Sets parameters (replacing any existing)
		 
		public X parameters(Map<String, ?> parameters) {
			this.parameters = new Multival(parameters);
			return getX();
		}
		 */
		protected abstract X getX(); //Generic trick

	}

	/**
	 * Base for HttpSender's fluent builders
	 * 
	 * @author martin.vanek
	 *
	 */
	public static abstract class SenderRequestBuilder<X extends SenderRequestBuilder<?>> extends HttlRequestBuilder<X> {

		public SenderRequestBuilder(HttlSender httpSender, Method method, String urlPath) {
			super(httpSender, method, urlPath);
		}

		public abstract HttlRequest build();

		/**
		 * Buid and execute Request and then return raw unprocessed Response.
		 * Response is open and caller is responsibe for closing.
		 */
		public HttlResponse execute() {
			HttlRequest request = build();
			return sender.execute(request);
		}

		/**Buid and execute Request and then let ResponseHandler parameter to process Response.
		 */
		public void execute(HttlResponseHandler handler) {
			HttlRequest request = build();
			sender.execute(request, handler);
		}

		/**
		 * Buid and execute Request and then extract response.
		 */
		public <T> ExtractedResponse<T> extract(Class<T> clazz) {
			HttlRequest request = build();
			return sender.extract(request, clazz);
		}

		/**
		 * Buid and execute Request and then extract response.
		 */
		public <T> ExtractedResponse<T> extract(GenericType<T> typeReference) {
			HttlRequest request = build();
			return sender.extract(request, typeReference);
		}

		/**
		 * Buid and execute Request and then extract response.
		 */
		public <T> ExtractedResponse<T> extract(HttlResponseExtractor<T> extractor) {
			HttlRequest request = build();
			return sender.extract(request, extractor);
		}

		public Future<HttlResponse> start() {
			HttlRequest request = build();
			return sender.start(request);
		}

		public <T> Future<ExtractedResponse<T>> start(HttlResponseExtractor<T> extractor) {
			HttlRequest request = build();
			return sender.start(request, extractor);
		}

		public <T> Future<ExtractedResponse<T>> start(Class<T> resultType) {
			HttlRequest request = build();
			return sender.start(request, resultType);
		}

		public void start(HttlResponseHandler handler) {
			HttlRequest request = build();
			sender.start(request, handler);
		}
	}

	/**
	 * GET, HEAD, DELETE, OPTIONS
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderNobodyRequestBuilder extends SenderRequestBuilder<SenderNobodyRequestBuilder> {

		public SenderNobodyRequestBuilder(HttlSender httpSender, Method method, String urlPath) {
			super(httpSender, method, urlPath);
		}

		@Override
		protected SenderNobodyRequestBuilder getX() {
			return this;
		}

		public HttlRequest build() {
			List<HttlBuilderVisitor> interceptors = sender.getConfig().getBuilderVisitors();
			for (HttlBuilderVisitor interceptor : interceptors) {
				interceptor.visit(this);
			}
			return new HttlRequest(sender, method, urlPath, parameters, headers, null, readTimeoutMillis);
		}
	}

	/**
	 * PUT, POST
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderBodyRequestBuilder extends SenderRequestBuilder<SenderBodyRequestBuilder> {

		public SenderBodyRequestBuilder(HttlSender httpSender, Method method, String path) {
			super(httpSender, method, path);
		}

		protected HttlBody body;

		/**
		 * payload can be byte[], String, InputStream, Reader or anything that can be marshalled
		 * 
		 */
		public SenderBodyRequestBuilder body(Object payload, String mediaType, boolean cache) {
			if (payload == null) {
				throw new HttlRequestException("Body object is null");
			}
			if (payload instanceof InputStream) {
				this.body = new HttlBody((InputStream) payload, cache);

			} else if (payload instanceof Reader) {
				this.body = new HttlBody(new ReaderInputStream((Reader) payload), cache);

			} else if (payload instanceof String) {
				this.body = new HttlBody((String) payload);

			} else if (payload instanceof byte[]) {
				this.body = new HttlBody((byte[]) payload);

			} else { //marshalling...

				if (mediaType == null) {
					mediaType = headers.getFirst(HttlConstants.Content_Type);
					if (mediaType == null) {
						mediaType = sender.getConfig().getDefaultHeaders().getFirst(HttlConstants.Content_Type);
					}
				}

				if (mediaType == null) {
					throw new HttlRequestException("Content-Type header is missing");
				}

				int indexOf = mediaType.indexOf(';');
				if (indexOf != -1) {
					mediaType = mediaType.substring(0, indexOf);
				}

				this.body = new HttlBody(payload, cache);
			}

			if (mediaType != null) {
				this.headers.set(HttlConstants.Content_Type, mediaType);
			}

			return getX();
		}

		public SenderBodyRequestBuilder body(Object body, String mediaType) {
			return body(body, mediaType, false);
		}

		/**
		 * Will make best effort to figure out what is body type and how it should be sent with request
		 */
		public SenderBodyRequestBuilder body(Object body) {
			return body(body, null);
		}

		@Override
		public HttlRequest build() {
			List<HttlBuilderVisitor> interceptors = sender.getConfig().getBuilderVisitors();
			for (HttlBuilderVisitor interceptor : interceptors) {
				interceptor.visit(this);
			}
			return new HttlRequest(sender, method, urlPath, parameters, headers, body, readTimeoutMillis);
		}

		@Override
		protected SenderBodyRequestBuilder getX() {
			return this;
		}

	}

}
