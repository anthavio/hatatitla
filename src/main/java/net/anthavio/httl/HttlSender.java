package net.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

import net.anthavio.httl.HttlExecutionChain.SenderExecutionChain;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlRequestBuilders.SenderBodyRequestBuilder;
import net.anthavio.httl.HttlRequestBuilders.SenderNobodyRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.cache.CachedResponse;
import net.anthavio.httl.transport.HttpUrlConfig;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.GenericType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlSender implements SenderOperations, Closeable {

	/**
	 * Build HttpSender with underlying HttpUrlTransport
	 */
	public static HttpUrlConfig For(String url) {
		return new HttpUrlConfig(url);
	}

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttlTransport transport;

	private final SenderBuilder config;

	private final ExecutorService executor; //can be null

	private final HttlBodyMarshaller marshaller;

	private final HttlBodyUnmarshaller unmarshaller;

	private final List<HttlExecutionInterceptor> executionInterceptors;

	public HttlSender(SenderBuilder config, HttlTransport transport) {
		if (config == null) {
			throw new IllegalArgumentException("null config");
		}
		this.config = config;
		this.transport = transport;
		this.executor = config.getExecutorService();
		this.marshaller = config.getMarshaller();
		this.unmarshaller = config.getUnmarshaller();
		this.executionInterceptors = config.getExecutionInterceptors();
	}

	public HttlTransport getTransport() {
		return transport;
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public SenderBuilder getConfig() {
		return config;
	}

	/**
	 * Extremely important for caching -  generates proper key based on information from request and sender
	 */
	public String getCacheKey(HttlRequest request) {
		return String.valueOf(config.getUrl().toString() + 31 * request.hashCode());
	}

	public void close() {
		transport.close();
	}

	HttlResponse doExecute(HttlRequest request) throws IOException {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug(request.getMethod() + " " + request.getUrl());
		}
		return transport.call(request);
	}

	/**
	 * Execute Request and return raw unprocessed Response.
	 * Response is left open and caller is responsibe for closing it.
	 */
	public HttlResponse execute(HttlRequest request) throws HttlRequestException {

		HttlResponse response = null;
		try {
			if (executionInterceptors != null && executionInterceptors.size() != 0) {
				response = new SenderExecutionChain(executionInterceptors, this).next(request);
			} else {
				response = doExecute(request);
			}
		} catch (Exception x) {
			Cutils.close(response);
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			} else {
				throw new HttlRequestException(x);
			}

		}

		return response;
	}

	/**
	 * Execute Request and use ResponseHandler parameter to process Response.
	 * Response is closed automaticaly.
	 * 
	 */
	public void execute(HttlRequest request, HttlResponseHandler handler) throws HttlException {
		if (handler == null) {
			throw new IllegalArgumentException("null handler");
		}
		HttlResponse response = null;
		try {
			response = execute(request);
		} catch (HttlException sx) {
			handler.onFailure(request, sx.getException());
			return;
		} catch (Exception x) {
			handler.onFailure(request, x);
			return;
		}

		try {
			handler.onResponse(response);
		} catch (Exception x) {
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			} else {
				throw new HttlProcessingException(x);
			}
		} finally {
			Cutils.close(response);
		}

	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 * ResponseExtractor is created and used to extract the specified resultType
	 */
	public <T> ExtractedResponse<T> extract(HttlRequest request, Class<T> resultType) throws HttlException {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		HttlResponse response = execute(request);
		return extract(response, resultType);
	}

	/**
	 * 
	 */
	public <T> ExtractedResponse<T> extract(HttlRequest request, GenericType<T> resultType) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		HttlResponse response = execute(request);
		return extract(response, resultType.getParameterizedType());
	}

	/**
	 * Mainly for CachingSender and CachingExtractor to access HttpSenders extracting facilities
	 */
	public <T> ExtractedResponse<T> extract(HttlResponse response, Type resultType) throws HttlException {
		try {

			HttlBodyUnmarshaller unmarshaller = this.unmarshaller.supports(response, resultType);
			if (unmarshaller != null) {
				Object object = unmarshaller.unmarshall(response, resultType);
				return new ExtractedResponse<T>(response, (T) object);//XXX this cast may not checked/honored at all!!!
			} else {
				throw new HttlProcessingException("No Unmarshaller for response: " + response + " return type: " + resultType);
			}

		} catch (IOException iox) {
			throw new HttlProcessingException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller.
	 * 
	 * Response is closed automaticaly.
	*/
	public <T> ExtractedResponse<T> extract(HttlRequest request, HttlResponseExtractor<T> extractor) throws HttlException {
		if (extractor == null) {
			throw new IllegalArgumentException("ResponseExtractor is null");
		}
		HttlResponse response = null;
		try {
			response = execute(request);
			extractor = extractor.supports(response);
			if (extractor != null) {
				T extracted = (T) extractor.extract(response);
				return new ExtractedResponse<T>(response, extracted);
			} else { //extractor declared himself unfit for this response
				Class<T> resultType = (Class<T>) ((ParameterizedType) extractor.getClass().getGenericSuperclass())
						.getActualTypeArguments()[0];
				HttlBodyUnmarshaller unmarshaller = this.unmarshaller.supports(response, resultType);
				if (unmarshaller != null) {
					Object extract = unmarshaller.unmarshall(response, resultType);
					if (extract instanceof RuntimeException) {
						throw (RuntimeException) extract;
					} else if (extract instanceof Exception) {
						throw new HttlProcessingException((Exception) extract);
					}
				} else {
					throw new HttlProcessingException("No Unmarshaller for response: " + response + " return type: " + resultType);
				}
			}
			return null;
		} catch (IOException iox) {
			throw new HttlProcessingException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T> Future<ExtractedResponse<T>> start(final HttlRequest request, final HttlResponseExtractor<T> extractor) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<ExtractedResponse<T>>() {

			@Override
			public ExtractedResponse<T> call() throws Exception {
				return extract(request, extractor);
			}
		});
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T> Future<ExtractedResponse<T>> start(final HttlRequest request, final Class<T> resultType) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<ExtractedResponse<T>>() {

			@Override
			public ExtractedResponse<T> call() throws Exception {
				return extract(request, resultType);
			}
		});
	}

	/**
	 * Asynchronous execution whith ResponseHandler
	 */
	public void start(final HttlRequest request, final HttlResponseHandler handler) {
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
	public Future<HttlResponse> start(final HttlRequest request) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<HttlResponse>() {

			@Override
			public HttlResponse call() throws Exception {
				HttlResponse response = execute(request);
				//too dangerous to left the SenderResponse unclosed
				//we will cache the response...
				return new CachedResponse(request, response);
			}
		});
	}

	/**
	 * Fluent builders 
	 */

	public SenderNobodyRequestBuilder GET(String path) {
		return new SenderNobodyRequestBuilder(this, Method.GET, path);
	}

	public SenderNobodyRequestBuilder HEAD(String path) {
		return new SenderNobodyRequestBuilder(this, Method.HEAD, path);
	}

	public SenderNobodyRequestBuilder TRACE(String path) {
		return new SenderNobodyRequestBuilder(this, Method.TRACE, path);
	}

	public SenderNobodyRequestBuilder OPTIONS(String path) {
		return new SenderNobodyRequestBuilder(this, Method.OPTIONS, path);
	}

	public SenderNobodyRequestBuilder DELETE(String path) {
		return new SenderNobodyRequestBuilder(this, Method.DELETE, path);
	}

	public SenderBodyRequestBuilder POST(String path) {
		return new SenderBodyRequestBuilder(this, Method.POST, path);
	}

	public SenderBodyRequestBuilder PUT(String path) {
		return new SenderBodyRequestBuilder(this, Method.PUT, path);
	}

	public SenderBodyRequestBuilder PATCH(String path) {
		return new SenderBodyRequestBuilder(this, Method.PATCH, path);
	}

	@Override
	public String toString() {
		return "HttpSender [" + config.getUrl() + ", executor=" + executor + "]";
	}

	public static class HttpHeaders extends Multival<String> {

		private static final long serialVersionUID = 1L;

		public HttpHeaders() {

		}
	}

	public static class Parameters extends Multival<String> {

		private static final long serialVersionUID = 1L;

	}

	/**
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class Multival<T> implements Iterable<String>, Serializable {

		private static final long serialVersionUID = 1L;

		private Map<String, List<T>> entries;

		protected Multival() {
		}

		/**
		 * Set value(s) replacing existing
		 * 
		 */
		public void set(String name, Collection<T> values) {
			if (values != null && values.size() != 0) {
				List<T> list = get(name, true);
				for (T value : values) {
					list.add(value);
				}
			} else {
				get(name, true);//.add(null);
			}
		}

		/**
		 * Set value(s) replacing existing
		 * 
		 */
		public void set(String name, T... values) {
			if (values != null && values.length != 0) {
				List<T> list = get(name, true);
				for (T value : values) {
					list.add(value);
				}
			} else {
				get(name, true);//.add(null);
			}

		}

		/**
		 * Set value(s) replacing existing
		 * 
		 */
		public void set(String name, T value) {
			get(name, true).add(value);
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, Collection<T> values) {
			if (values != null && values.size() != 0) {
				List<T> list = get(name, false);
				for (T value : values) {
					list.add(value);
				}
			} else {
				get(name, false);//.add(null);
			}
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, T... values) {
			if (values != null && values.length != 0) {
				List<T> list = get(name, false);
				for (T value : values) {
					list.add(value);
				}
			} else {
				get(name, false);//.add(null);
			}
		}

		/**
		 * Add value(s) keeping existing
		 */
		public void add(String name, T value) {
			get(name, false).add(value);
		}

		public void put(String name, T value, boolean clear) {
			get(name, clear).add(value);
		}

		protected List<T> get(String name, boolean clear) {
			if (Cutils.isBlank(name)) {
				throw new IllegalArgumentException("Name is blank");
			}
			List<T> list = null;
			if (this.entries == null) {
				this.entries = new TreeMap<String, List<T>>();
			} else {
				list = this.entries.get(name);
			}
			if (list == null) {
				list = new LinkedList<T>();
				this.entries.put(name, list);
			} else if (clear) {
				list.clear();
			}
			return list;

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

		public List<T> get(String name) {
			if (this.entries == null) {
				return null;
			} else {
				return this.entries.get(name);
			}
		}

		public T getFirst(String name) {
			List<T> values = get(name);
			if (values != null && values.size() != 0) {
				return values.get(0);
			} else {
				return null;
			}
		}

		public T getLast(String name) {
			List<T> values = get(name);
			if (values != null && values.size() != 0) {
				return values.get(values.size() - 1);
			} else {
				return null;
			}
		}

		public void clear() {
			if (this.entries != null) {
				entries.clear();
			}
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
