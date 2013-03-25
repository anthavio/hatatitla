package com.anthavio.hatatitla;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.hatatitla.SenderRequestBuilders.SenderDeleteRequestBuilder;
import com.anthavio.hatatitla.SenderRequestBuilders.SenderGetRequestBuilder;
import com.anthavio.hatatitla.SenderRequestBuilders.SenderPostRequestBuilder;
import com.anthavio.hatatitla.SenderRequestBuilders.SenderPutRequestBuilder;
import com.anthavio.hatatitla.async.ResponseHandler;
import com.anthavio.hatatitla.cache.CachedResponse;
import com.anthavio.hatatitla.inout.RequestBodyMarshaller;
import com.anthavio.hatatitla.inout.RequestBodyMarshallers;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor;
import com.anthavio.hatatitla.inout.ResponseBodyExtractors;
import com.anthavio.hatatitla.inout.ResponseExtractorFactory;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class HttpSender implements Closeable {

	public static enum NullValueHandling {
		SKIP_IGNORE, EMPTY_STRING;
	}

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSenderConfig config;

	private ExecutorService executor;

	private RequestBodyMarshallers marshallers = new RequestBodyMarshallers();

	private ResponseBodyExtractors extractors = new ResponseBodyExtractors();

	private NullValueHandling nullHandling = NullValueHandling.EMPTY_STRING;

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

	public void setRequestMarshaller(String mimeType, RequestBodyMarshaller marshaller) {
		marshallers.setMarshaller(mimeType, marshaller);
	}

	public ResponseExtractorFactory getResponseExtractor(String mimeType) {
		return extractors.getExtractorFactory(mimeType);
	}

	public void setResponseExtractor(ResponseExtractorFactory factory, String mimeType) {
		extractors.setExtractorFactory(factory, mimeType);
	}

	/**
	 * Way to set extractor for error responses
	 */
	public void setResponseExtractor(ResponseExtractorFactory factory, String mimeType, int... httpStatus) {
		extractors.setExtractorFactory(factory, mimeType, httpStatus);
	}

	/**
	 * To be implemented by concrete HttpSender
	 */
	protected abstract SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException;

	/**
	 * Extremely important for caching -  generates proper key based on information from request and sender
	 */
	public String getCacheKey(SenderRequest request) {
		return String.valueOf(config.getHostUrl().toString().hashCode() * 31 + request.hashCode());
	}

	/**
	 * Response returning version. Caller must close Response
	 */
	public SenderResponse execute(SenderRequest request) throws IOException {
		request.setSender(this);
		String[] pathquery = getPathAndQuery(request);
		String path = pathquery[0];
		String query = pathquery[1];

		if (this.logger.isDebugEnabled()) {
			this.logger.debug(request.getMethod() + " " + path);
		}

		return doExecute(request, path, query);
	}

	/**
	 * Response handler version. Response will be close automaticaly
	 */
	public void execute(SenderRequest request, ResponseHandler handler) throws IOException {
		if (handler == null) {
			throw new IllegalArgumentException("null handler");
		}
		SenderResponse response = execute(request);
		try {
			handler.onResponse(response);
		} catch (Exception x) {
			handler.onError(x);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 * ResponseExtractor is created for the paricular returnType
	 */
	public <T extends Serializable> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType)
			throws IOException {
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
	 * Mainly for CachingSender and CachingExtractor
	 */
	public <T extends Serializable> T extract(SenderResponse response, Class<T> resultType) throws IOException {
		if (response.getHttpStatusCode() >= 300) {
			//XXX error response extractor
			throw new SenderHttpStatusException(response);
		}
		try {
			return extractors.extract(response, resultType);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T extends Serializable> ExtractedBodyResponse<T> extract(SenderRequest request,
			ResponseBodyExtractor<T> extractor) throws IOException {
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor is null");
		}
		SenderResponse response = execute(request);
		if (response.getHttpStatusCode() >= 300) {
			//XXX error response extractor
			throw new SenderHttpStatusException(response);
		}
		try {
			T extracted = extractor.extract(response);
			return new ExtractedBodyResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T extends Serializable> Future<ExtractedBodyResponse<T>> start(final SenderRequest request,
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
	public <T extends Serializable> Future<ExtractedBodyResponse<T>> start(final SenderRequest request,
			final Class<T> resultType) {
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
					handler.onError(x);
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

	public SenderPostRequestBuilder POST(String path) {
		return new SenderPostRequestBuilder(this, path);
	}

	public SenderPutRequestBuilder PUT(String path) {
		return new SenderPutRequestBuilder(this, path);
	}

	/**
	 * Shared helper to build url path and query
	 */
	protected String[] getPathAndQuery(SenderRequest request) {
		//String path = joinPath(request.getUrlPath());
		String encoding = config.getEncoding();
		Multival parameters = request.getParameters();
		StringBuilder sbMxParams = null;
		StringBuilder sbQuParams = null;
		boolean bQp = false; //is any query parameter
		//XXX multivalue parameter encode paramA=val1,val2
		if (parameters != null && parameters.size() != 0) {
			sbMxParams = new StringBuilder();
			sbQuParams = new StringBuilder();
			for (String name : parameters) {
				if (name.charAt(0) == ';') { //matrix parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbMxParams.append(';');//keep unescaped
						sbMxParams.append(urlencode(name.substring(1), encoding));
						sbMxParams.append('=');
						//XXX matrix parameters may contain / and that / must be unescaped
						sbMxParams.append(urlencode(value, encoding));
					}
				} else { //query parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbQuParams.append(urlencode(name, encoding));
						sbQuParams.append('=');
						sbQuParams.append(urlencode(value, encoding));
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
				path = path + "?" + sbQuParams.toString();// GET, DELETE
			} else if (request.hasBody()) {
				path = path + "?" + sbQuParams.toString(); // POST, PUT with body
			}
		}
		String query = sbQuParams != null ? sbQuParams.toString() : null;
		return new String[] { path, query };
	}

	private final String urlencode(String string, String encoding) {
		try {
			return URLEncoder.encode(string, encoding);
		} catch (UnsupportedEncodingException uex) {
			throw new IllegalStateException("Misconfigured encoding " + encoding, uex);
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

		public Multival(Map<String, List<String>> entries) {
			this.entries = new TreeMap<String, List<String>>(COMPARATOR);// response header from HttpUrlConnection has null header name for status line
			this.entries.putAll(entries);
			Set<Entry<String, List<String>>> entrySet = this.entries.entrySet();
			for (Entry<String, List<String>> entry : entrySet) {
				List<String> values = entry.getValue();
				LinkedList<String> valuesCopy = new LinkedList<String>(values);
				if (values == null || values.size() == 0) {
					valuesCopy.add("");
				}
				entry.setValue(valuesCopy);
			}
		}

		public Multival(List<String[]> values) {
			if (values != null) {
				for (String[] value : values) {
					if (value.length == 0) {
						//continue;
					} else if (value.length == 1) {
						add(value[0], ""); //NullValueHandling.EMPTY_STRING
					} else if (value.length == 2) {
						add(value[0], value[1]);
					} else {
						String[] others = new String[value.length - 1];
						System.arraycopy(value, 1, others, 0, value.length - 1);
						add(value[0], others);
					}
				}
			}
		}

		/**
		 * Set Header value(s) replacing existing
		 */
		public void set(String name, String... value) {
			if (this.entries != null) {
				List<String> list = this.entries.get(name);
				if (list != null) {
					list.clear();
				}
			}
			add(name, value);
		}

		/**
		 * Set Header value(s) replacing existing
		 */
		public void set(String name, List<String> values) {
			set(name, values.toArray(new String[values.size()]));
		}

		/**
		 * Add Header value(s) keeping existing
		 */
		public void add(String name, List<String> values) {
			add(name, values.toArray(new String[values.size()]));
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, String... values) {
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
			}

			for (String value : values) {
				if (value != null) {
					list.add(value);
				} else {//if(nullHandling==NullValueHandling.EMPTY_STRING) {
					list.add("");
				}
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
			if (values != null) {
				return values.get(0);
			} else {
				return null;
			}
		}

		public String getLast(String name) {
			List<String> values = get(name);
			if (values != null) {
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
