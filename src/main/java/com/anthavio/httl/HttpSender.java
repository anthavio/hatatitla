package com.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.httl.SenderRequest.ValueStrategy;
import com.anthavio.httl.SenderRequestBuilders.SenderDeleteRequestBuilder;
import com.anthavio.httl.SenderRequestBuilders.SenderGetRequestBuilder;
import com.anthavio.httl.SenderRequestBuilders.SenderHeadRequestBuilder;
import com.anthavio.httl.SenderRequestBuilders.SenderOptionsRequestBuilder;
import com.anthavio.httl.SenderRequestBuilders.SenderPostRequestBuilder;
import com.anthavio.httl.SenderRequestBuilders.SenderPutRequestBuilder;
import com.anthavio.httl.cache.CachedResponse;
import com.anthavio.httl.inout.RequestBodyMarshaller;
import com.anthavio.httl.inout.RequestBodyMarshallers;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.inout.ResponseBodyExtractors;
import com.anthavio.httl.inout.ResponseBodyHandler;
import com.anthavio.httl.inout.ResponseErrorHandler;
import com.anthavio.httl.inout.ResponseExtractorFactory;
import com.anthavio.httl.inout.ResponseHandler;
import com.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class HttpSender implements SenderOperations, Closeable {

	public static enum NullValueHandling {
		SKIP_IGNORE, EMPTY_STRING;
	}

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSenderConfig config;

	private ExecutorService executor;

	private RequestBodyMarshallers marshallers = new RequestBodyMarshallers();

	private ResponseBodyExtractors extractors = new ResponseBodyExtractors();

	private ResponseErrorHandler errorResponseHandler;

	public HttpSender(HttpSenderConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("null config");
		}
		this.config = config;
	}

	public HttpSender(HttpSenderConfig config, ExecutorService executor) {
		this(config);
		if (executor == null) {
			throw new IllegalArgumentException("null executor");
		}
		this.executor = executor;
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public HttpSenderConfig getConfig() {
		return config;
	}

	public RequestBodyMarshaller getRequestMarshaller(String mimeType) {
		return marshallers.getMarshaller(mimeType);
	}

	/**
	 * Sets RequestBodyMarshaller for specified request mimeType (from Content-Type header)
	 */
	public void setRequestMarshaller(RequestBodyMarshaller marshaller, String mimeType) {
		marshallers.setMarshaller(marshaller, mimeType);
	}

	public ResponseExtractorFactory getResponseExtractorFactory(String mimeType) {
		return extractors.getExtractorFactory(mimeType);
	}

	/**
	 * Sets ResponseExtractorFactory for specified response mimeType (from Content-Type header)
	 */
	public void setResponseExtractorFactory(ResponseExtractorFactory factory, String mimeType) {
		extractors.setExtractorFactory(factory, mimeType);
	}

	/**
	 * @return global ResponseErrorHandler
	 */
	public ResponseErrorHandler getErrorResponseHandler() {
		return errorResponseHandler;
	}

	/**
	 * Sets global ResponseErrorHandler
	 * It is used when extract is called but http status >= 300 is returned from server
	 * null parameter value unsets 
	 */
	public void setErrorResponseHandler(ResponseErrorHandler errorResponseHandler) {
		this.errorResponseHandler = errorResponseHandler;
	}

	/**
	 * Extremely important for caching -  generates proper key based on information from request and sender
	 */
	public String getCacheKey(SenderRequest request) {
		return String.valueOf(config.getHostUrl().toString().hashCode() * 31 + request.hashCode());
	}

	/**
	 * To be implemented by concrete HttpSender
	 */
	protected abstract SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException;

	/**
	 * Remove annoying throws IOException from Closeable
	 */
	@Override
	public abstract void close();

	/**
	 * Execute Request and return raw unprocessed Response.
	 * Response is lefty open and caller is responsibe for closing.
	 */
	public SenderResponse execute(SenderRequest request) throws SenderException {
		request.setSender(this);
		String[] pathquery = getPathAndQuery(request);
		String path = pathquery[0];
		String query = pathquery[1];

		if (this.logger.isDebugEnabled()) {
			this.logger.debug(request.getMethod() + " " + path);
		}
		try {
			return doExecute(request, path, query);
		} catch (IOException iox) {
			throw new SenderException(iox);
		}
	}

	/**
	 * Execute Request and use ResponseHandler parameter to process Response.
	 * Response is closed automaticaly.
	 * 
	 */
	public void execute(SenderRequest request, ResponseHandler handler) throws SenderException {
		if (handler == null) {
			throw new IllegalArgumentException("null handler");
		}
		SenderResponse response = null;
		try {
			response = execute(request);
			handler.onResponse(response);
		} catch (Exception x) {
			if (response == null) {
				handler.onRequestError(request, x);
			} else {
				handler.onResponseError(response, x);
			}
		} finally {
			Cutils.close(response);
		}

	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller.
	 * 
	 * Response is closed automaticaly.
	 */
	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, ResponseBodyExtractor<T> extractor)
			throws SenderException {
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor is null");
		}
		SenderResponse response = null;
		try {
			response = execute(request);
			if (response.getHttpStatusCode() >= 300) {
				if (errorResponseHandler != null) {
					errorResponseHandler.onErrorResponse(response);
					return new ExtractedBodyResponse<T>(response, null);
				} else {
					throw new SenderHttpStatusException(response);
				}
			}
			T extracted = extractor.extract(response);
			return new ExtractedBodyResponse<T>(response, extracted);
		} catch (IOException iox) {
			throw new SenderException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Execute Request and use ResponseBodyHandler parameter to process Response.
	 * 
	 * Response is closed automaticaly.
	 * 
	 */
	public <T> void execute(SenderRequest request, ResponseBodyHandler<T> handler) throws SenderException {
		if (handler == null) {
			throw new IllegalArgumentException("null handler");
		}
		SenderResponse response = null;
		try {

			try {
				response = execute(request);

				Class<T> clazz = (Class<T>) ((ParameterizedType) handler.getClass().getGenericSuperclass())
						.getActualTypeArguments()[0];
				ExtractedBodyResponse<T> extracted = extract(request, clazz);
				handler.onResponse(response, extracted.getBody());
			} catch (Exception x) {
				if (response == null) {
					handler.onRequestError(request, x);
				} else {
					handler.onResponseError(response, x);
				}
			}

		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 * ResponseExtractor is created and used to extract the specified resultType
	 */
	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType) throws SenderException {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		SenderResponse response = execute(request);
		try {
			T extracted = extract(response, resultType);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Mainly for CachingSender and CachingExtractor to access HttpSenders extracting facilities
	 */
	public <T> T extract(SenderResponse response, Class<T> resultType) throws SenderException {
		try {
			if (response.getHttpStatusCode() >= 300) {
				if (errorResponseHandler != null) {
					errorResponseHandler.onErrorResponse(response);
					return null;
				} else {
					throw new SenderHttpStatusException(response);
				}
			}
			return extractors.extract(response, resultType);
		} catch (IOException iox) {
			throw new SenderException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T> Future<ExtractedBodyResponse<T>> start(final SenderRequest request,
			final ResponseBodyExtractor<T> extractor) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<ExtractedBodyResponse<T>>() {

			@Override
			public ExtractedBodyResponse<T> call() throws Exception {
				return extract(request, extractor);
			}
		});
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T> Future<ExtractedBodyResponse<T>> start(final SenderRequest request, final Class<T> resultType) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<ExtractedBodyResponse<T>>() {

			@Override
			public ExtractedBodyResponse<T> call() throws Exception {
				return extract(request, resultType);
			}
		});
	}

	/**
	 * Asynchronous execution whith ResponseHandler
	 */
	public void start(final SenderRequest request, final ResponseHandler handler) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					execute(request, handler);
				} catch (Exception x) {
					logger.warn("Failed asynchronous request", x);
				}
			}
		});
	}

	public <T> void start(final SenderRequest request, final ResponseBodyHandler<T> handler) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					execute(request, handler);
				} catch (Exception x) {
					logger.warn("Failed asynchronous request", x);
				}
			}
		});
	}

	/**
	 * Asynchronous execution with Future as response
	 */
	public Future<SenderResponse> start(final SenderRequest request) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<SenderResponse>() {

			@Override
			public SenderResponse call() throws Exception {
				SenderResponse response = execute(request);
				//too dangerous to left the SenderResponse unclosed
				//we will cache the response...
				return new CachedResponse(request, response);
			}
		});
	}

	/**
	 * Fluent builders 
	 */

	public SenderGetRequestBuilder GET(String path) {
		return new SenderGetRequestBuilder(this, path);
	}

	public SenderDeleteRequestBuilder DELETE(String path) {
		return new SenderDeleteRequestBuilder(this, path);
	}

	public SenderHeadRequestBuilder HEAD(String path) {
		return new SenderHeadRequestBuilder(this, path);
	}

	public SenderOptionsRequestBuilder OPTIONS(String path) {
		return new SenderOptionsRequestBuilder(this, path);
	}

	public SenderPostRequestBuilder POST(String path) {
		return new SenderPostRequestBuilder(this, path);
	}

	public SenderPutRequestBuilder PUT(String path) {
		return new SenderPutRequestBuilder(this, path);
	}

	/**
	 * Shared helper to build url path and query
	 * 
	 * Returns full path with query on first index and query string on second
	 */
	protected String[] getPathAndQuery(SenderRequest request) {
		ValueStrategy nullStrategy = request.getNullValueStrategy() != null ? request.getNullValueStrategy() : config
				.getNullValueStrategy();
		ValueStrategy emptyStrategy = request.getEmptyValueStrategy() != null ? request.getEmptyValueStrategy() : config
				.getEmptyValueStrategy();
		StringBuilder sbMxParams = null;
		StringBuilder sbQuParams = null;
		boolean bQp = false; //is any query parameter
		//XXX multivalue parameter encode paramA=val1,val2
		Multival parameters = request.getParameters();
		if (parameters != null && parameters.size() != 0) {
			sbMxParams = new StringBuilder();
			sbQuParams = new StringBuilder();
			for (String name : parameters) {
				if (name.charAt(0) == ';') { //matrix parameter
					List<String> values = parameters.get(name);
					if (values == null) {
						if (nullStrategy == ValueStrategy.SKIP) {
							logger.debug("skipping null parameter " + name);
							continue;
						}
					} else if (isEmpty(values, nullStrategy, emptyStrategy)) {
						if (emptyStrategy == ValueStrategy.SKIP) {
							logger.debug("skipping empty parameter " + name);
							continue;
						}
					}
					for (String value : values) {
						if (value == null) {
							if (nullStrategy == ValueStrategy.SKIP) {
								logger.debug("skipping null parameter " + name);
								continue;
							}
						} else if (Cutils.isEmpty(value)) {
							if (emptyStrategy == ValueStrategy.SKIP) {
								logger.debug("skipping empty parameter " + name);
								continue;
							}
						}
						sbMxParams.append(';');//keep ; unescaped
						sbMxParams.append(urlencode(name.substring(1)));
						if (value != null) {
							sbMxParams.append('=');
							//XXX matrix parameters may contain '/' and it must stay unescaped
							sbMxParams.append(urlencode(value));
						}
					}
				} else { //query parameter
					List<String> values = parameters.get(name);
					if (values == null) {
						if (nullStrategy == ValueStrategy.SKIP) {
							logger.debug("skipping null parameter " + name);
							continue;
						}
					} else if (isEmpty(values, nullStrategy, emptyStrategy)) {
						if (emptyStrategy == ValueStrategy.SKIP) {
							logger.debug("skipping empty parameter " + name);
							continue;
						}
					}
					for (String value : values) {
						if (value == null) {
							if (nullStrategy == ValueStrategy.SKIP) {
								logger.debug("skipping null parameter " + name);
								continue;
							}
						} else if (Cutils.isEmpty(value)) {
							if (emptyStrategy == ValueStrategy.SKIP) {
								logger.debug("skipping empty parameter " + name);
								continue;
							}
						}
						sbQuParams.append(urlencode(name));
						if (value != null) {
							sbQuParams.append('=');
							sbQuParams.append(urlencode(value));
						}
						sbQuParams.append('&');
						bQp = true;
					}
				}
			}
			//remove trailing '&'
			if (bQp) {
				sbQuParams.delete(sbQuParams.length() - 1, sbQuParams.length());
			}
		}

		String path = request.getUrlPath();

		//append matrix parameters if any
		if (sbMxParams != null && sbMxParams.length() != 0) {
			path = path + sbMxParams;
		}

		//append query parameters if are any and if apropriate
		if (bQp) {
			if (!request.getMethod().canHaveBody()) {
				path = path + "?" + sbQuParams.toString();// GET, DELETE, ... (body not allowed)
			} else if (request.hasBody()) {
				path = path + "?" + sbQuParams.toString(); // POST, PUT with body - query must be part of path
			}
		}
		String query = sbQuParams != null ? sbQuParams.toString() : null;
		return new String[] { path, query };
	}

	private boolean isEmpty(List<String> values, ValueStrategy nullStrategy, ValueStrategy emptyStrategy) {
		if (values.size() != 0) {
			for (String value : values) {
				if (value == null) {
					if (nullStrategy == ValueStrategy.KEEP) {
						return true;
					}
				} else if (Cutils.isEmpty(value)) {
					if (emptyStrategy == ValueStrategy.KEEP) {
						return true;
					}
				} else {
					return true;
				}

			}
		}
		return false;
	}

	private final String urlencode(String string) {
		try {
			return URLEncoder.encode(string, "utf-8"); //W3C recommends utf-8 
		} catch (UnsupportedEncodingException uex) {
			throw new IllegalStateException("Misconfigured encoding utf-8", uex);
		}
	}

	@Override
	public String toString() {
		return "HttpSender [" + config.getHostUrl() + ", executor=" + executor + "]";
	}

	/**
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class Multival implements Iterable<String>, Serializable {

		private static final long serialVersionUID = 1L;

		private Map<String, List<String>> entries;

		public Multival() {

		}

		public Multival(Map<String, ?> values) {
			if (values != null && values.size() != 0) {
				this.entries = new TreeMap<String, List<String>>(COMPARATOR);// response header from HttpUrlConnection has null header name for status line
				Set<String> keySet = values.keySet();
				for (String key : keySet) {
					List<String> list = new LinkedList<String>();
					addValue(values.get(key), list);
					this.entries.put(key, list);
				}
			}
		}

		/**
		 * Set value(s) replacing existing
		 * 
		 * Null or Empty @param value removes parameter
		 */
		public void set(String name, Collection<Object> value) {
			put(name, value, true);
		}

		/**
		 * Set value(s) replacing existing
		 * 
		 * Null or Empty @param value removes this parameter
		 */
		public void set(String name, Object... value) {
			put(name, value, true);
		}

		/**
		 * Set value(s) replacing existing
		 * 
		 * Null or Empty @param value removes this parameter
		 */
		public void set(String name, Object value) {
			put(name, value, true);
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, Collection<Object> value) {
			if (value != null && value.size() != 0) {
				put(name, value, false);
			}
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, Object... values) {
			put(name, values, false);
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, Object values) {
			put(name, values, false);
		}

		/**
		 * Ultimate name/value method
		 */
		public void put(String name, Object value, boolean replace) {
			if (Cutils.isBlank(name)) {
				throw new IllegalArgumentException("Name is blank");
			}
			List<String> list = null;
			if (this.entries == null) {
				this.entries = new TreeMap<String, List<String>>();
			} else {
				list = this.entries.get(name);
			}
			if (list == null) {
				list = new LinkedList<String>();
				this.entries.put(name, list);
			} else if (replace) {
				list.clear();
			}

			addValue(value, list);
			if (list.size() == 0) {
				this.entries.remove(name);
			}
		}

		/**
		 * Ultimate value method
		 */
		private void addValue(Object value, List<String> list) {
			if (value == null) {
				list.add(null);
			} else if (value instanceof Collection) {
				Collection<?> collection = (Collection<?>) value;
				for (Object item : collection) {
					addValue(item, list);
				}
			} else if (value.getClass().isArray()) {
				int length = Array.getLength(value);
				for (int i = 0; i < length; ++i) {
					Object item = Array.get(value, i);
					addValue(item, list);
				}
			} else if (value instanceof Iterator) {
				Iterator<?> iterator = (Iterator<?>) value;
				while (iterator.hasNext()) {
					addValue(iterator.next(), list);
				}
			} else if (value instanceof Serializable) {
				String string = String.valueOf(value);
				list.add(string);
			} else {
				throw new IllegalArgumentException("Unsupported " + value.getClass() + " of value " + value);
			}
		}

		public Set<String> names() {
			if (this.entries == null) {
				return Collections.emptySet();
			} else {
				return this.entries.keySet();
			}
		}

		public int size() {
			return this.entries == null ? 0 : this.entries.size();
		}

		public List<String> get(String name) {
			if (this.entries == null) {
				return null;
			} else {
				return this.entries.get(name);
			}
		}

		public String getFirst(String name) {
			List<String> values = get(name);
			if (values != null && values.size() != 0) {
				return values.get(0);
			} else {
				return null;
			}
		}

		public String getLast(String name) {
			List<String> values = get(name);
			if (values != null && values.size() != 0) {
				return values.get(values.size() - 1);
			} else {
				return null;
			}
		}

		public void clear() {
			entries.clear();
		}

		@Override
		public Iterator<String> iterator() {
			if (this.entries == null) {
				return Collections.<String> emptySet().iterator();
			}
			return names().iterator();
		}

		@Override
		public String toString() {
			return String.valueOf(this.entries);
		}

		@Override
		public int hashCode() {
			return this.entries == null ? 0 : this.entries.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Multival other = (Multival) obj;
			if (this.entries == null) {
				if (other.entries != null) {
					return false;
				}
			} else if (!this.entries.equals(other.entries)) {
				return false;
			}
			return true;
		}
	}

	private static final Comparator<String> COMPARATOR = new NullSafeStringComparator();

	private static class NullSafeStringComparator implements Comparator<String> {

		@Override
		public int compare(String o1, String o2) {
			if (o1 == o2) {
				return 0;
			} else if (o1 == null) {
				return -1;
			} else if (o2 == null) {
				return 1;
			} else {
				return o1.compareTo(o2);
			}
		}
	}

}
