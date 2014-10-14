package net.anthavio.httl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import net.anthavio.httl.util.HttlUtil;
import net.anthavio.httl.util.HttpDateUtil;

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

		public abstract HttlRequest build();

		/**
		 * Buid and execute Request and then return raw unprocessed Response.
		 * Response is open and caller is responsibe for closing.
		 */
		public HttlResponse execute() {
			HttlRequest request = build();
			return sender.execute(request);
		}

		/**
		 * Buid and execute Request and then let ResponseHandler parameter to process Response.
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

		@Override
		public String toString() {
			return "HttlRequestBuilder [sender=" + sender + ", method=" + method + ", urlPath=" + urlPath + ", headers="
					+ headers + ", parameters=" + parameters + "]";
		}

		protected abstract X getX(); //Generic trick

	}

	/**
	 * GET, HEAD, DELETE, OPTIONS
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class BodylessRequestBuilder extends HttlRequestBuilder<BodylessRequestBuilder> {

		public BodylessRequestBuilder(HttlSender httpSender, Method method, String urlPath) {
			super(httpSender, method, urlPath);
		}

		@Override
		protected BodylessRequestBuilder getX() {
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
	public static class BodyfulRequestBuilder extends HttlRequestBuilder<BodyfulRequestBuilder> {

		public BodyfulRequestBuilder(HttlSender httpSender, Method method, String path) {
			super(httpSender, method, path);
		}

		protected HttlBody body;

		/**
		 * Set body as String
		 */
		public BodyfulRequestBuilder body(String payload, String mediaType) {
			if (payload == null) {
				throw new HttlRequestException("Payload string is null");
			}
			setContentType(mediaType);
			this.body = new HttlBody(payload);
			return this;
		}

		/**
		 * Set request body as byte array
		 */
		public BodyfulRequestBuilder body(byte[] payload, String mediaType) {
			if (payload == null) {
				throw new HttlRequestException("Payload byte[] is null");
			}
			setContentType(mediaType);
			this.body = new HttlBody(payload);
			return this;
		}

		/**
		 * Set body as InputStream
		 */
		public BodyfulRequestBuilder body(InputStream stream, String mediaType, boolean buffer) {
			if (stream == null) {
				throw new HttlRequestException("Payload stream is null");
			}
			setContentType(mediaType);
			if (buffer) {
				try {
					byte[] bytes = HttlUtil.readAsBytes(stream, HttlUtil.KILO16);
					this.body = new HttlBody(bytes);
				} catch (IOException iox) {
					throw new HttlRequestException(iox);
				}
			} else {
				this.body = new HttlBody(stream);
			}
			return this;
		}

		/**
		 * Set body as Reader
		 */
		public BodyfulRequestBuilder body(Reader reader, String mediaType, boolean buffer) {
			if (reader == null) {
				throw new HttlRequestException("Payload reader is null");
			}
			setContentType(mediaType);
			if (buffer) {
				try {
					String string = HttlUtil.readAsString(reader, HttlUtil.KILO16);
					this.body = new HttlBody(string);
				} catch (IOException iox) {
					throw new HttlRequestException(iox);
				}
			} else {
				this.body = new HttlBody(reader);
			}

			return this;
		}

		/**
		 * Set body as non buffered
		 */
		public BodyfulRequestBuilder body(Object body, String mediaType) {
			return body(body, mediaType, false);
		}

		/**
		 * Will make best effort to figure out what is body type and how it should be sent with request
		 * 
		 * Content-Type header must be specified before
		 */
		public BodyfulRequestBuilder body(Object body) {
			return body(body, null);
		}

		private String[] setContentType(String mediaType) {
			if (mediaType == null) {
				mediaType = headers.getFirst(HttlConstants.Content_Type);
				if (mediaType == null) {
					mediaType = sender.getConfig().getDefaultHeaders().getFirst(HttlConstants.Content_Type);
					if (mediaType == null) {
						throw new HttlRequestException("Content-Type not found. Cannot set request body");
					}
				}
			}
			String[] mimeTypeAndCharset = HttlUtil.splitContentType(mediaType, sender.getConfig().getCharset());
			headers.set(HttlConstants.Content_Type, mimeTypeAndCharset[0] + "; charset=" + mimeTypeAndCharset[1]);
			return mimeTypeAndCharset;
		}

		/**
		 * payload can be byte[], String, InputStream, Reader or anything else that can be marshalled
		 */
		public BodyfulRequestBuilder body(Object payload, String mediaType, boolean buffer) {
			if (payload == null) {
				throw new HttlRequestException("Payload is null");
			}
			if (payload instanceof InputStream) {
				return body((InputStream) payload, mediaType, buffer);
			} else if (payload instanceof Reader) {
				return body((Reader) payload, mediaType, buffer);
			} else if (payload instanceof String) {
				return body((String) payload, mediaType);
			} else if (payload instanceof byte[]) {
				return body((byte[]) payload, mediaType);
			} else {
				//marshalling...
				String[] contentType = setContentType(mediaType);
				if (buffer) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						sender.getMarshaller().marshall(payload, contentType[0], contentType[1], baos);
					} catch (IOException iox) {
						throw new HttlRequestException(iox);
					}
					this.body = new HttlBody(baos.toByteArray());
				} else {
					this.body = new HttlBody(payload);
				}
			}
			return getX();
		}

		@Override
		public HttlRequest build() {
			List<HttlBuilderVisitor> visitors = sender.getConfig().getBuilderVisitors();
			for (HttlBuilderVisitor visitor : visitors) {
				visitor.visit(this);
			}
			return new HttlRequest(sender, method, urlPath, parameters, headers, body, readTimeoutMillis);
		}

		@Override
		protected BodyfulRequestBuilder getX() {
			return this;
		}

	}

	public static String[] digContentType(Multival<String> requestHeaders, SenderConfigurer config) {
		return digContentType(requestHeaders.getFirst(HttlConstants.Content_Type),
				config.getDefaultHeaders().getFirst(HttlConstants.Content_Type), config.getCharset());
	}

	/**
	 * @param requestContentType - can be null
	 * @param defaultMediaType - can be null
	 * @param defaultCharset = never null
	 * @return 
	 */
	public static String[] digContentType(String requestContentType, String defaultContentType, String defaultCharset) {
		String mediaType;
		String charset;
		if (requestContentType != null) {

			int idxMediaEnd = requestContentType.indexOf(";");
			if (idxMediaEnd != -1) {
				mediaType = requestContentType.substring(0, idxMediaEnd);
			} else {
				mediaType = requestContentType;
			}

			int idxCharset = requestContentType.indexOf("charset=");
			if (idxCharset != -1) {
				charset = requestContentType.substring(idxCharset + 8);
			} else {
				charset = defaultCharset;
			}
		} else if (defaultContentType != null) {
			int idxMediaEnd = defaultContentType.indexOf(";");
			if (idxMediaEnd != -1) {
				mediaType = defaultContentType.substring(0, idxMediaEnd);
			} else {
				mediaType = defaultContentType;
			}
			int idxCharset = defaultContentType.indexOf("charset=");
			if (idxCharset != -1) {
				charset = defaultContentType.substring(idxCharset + 8);
			} else {
				charset = defaultCharset;
			}
		} else {
			mediaType = null;
			charset = defaultCharset;
		}

		return new String[] { mediaType, charset };
	}

}
