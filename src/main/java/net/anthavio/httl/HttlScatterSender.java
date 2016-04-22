package net.anthavio.httl;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlRequestBuilder.BodyfulRequestBuilder;
import net.anthavio.httl.HttlRequestBuilder.BodylessRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.util.GenericType;

/**
 * 
 * @author mvanek
 *
 */
public class HttlScatterSender implements Closeable {

	private final ExecutorService executor;

	private final HttlSender[] senders;

	private final SenderConfigurer senderConfig;

	private final int readTimeoutMillis;

	public HttlScatterSender(HttlSender[] senders) {
		if (senders == null || senders.length == 0) {
			throw new IllegalArgumentException("Null or empty HttlSender array");
		}
		this.senders = senders;
		this.executor = Executors.newFixedThreadPool(senders.length, new NamingThreadFactory("cluster-http-"));
		this.senderConfig = senders[0].getConfig();
		this.readTimeoutMillis = senderConfig.getTransport().getConfig().getReadTimeoutMillis();
	}

	public HttlScatterSender(List<HttlSender> senders) {
		this(senders.toArray(new HttlSender[senders.size()]));
	}

	public HttlSender getSender(String hostName) {
		for (HttlSender sender : senders) {
			if (hostName.equals(sender.getConfig().getUrl().getHost())) {
				return sender;
			}
		}
		throw new IllegalArgumentException("HttlSender not found: " + hostName);
	}

	public <T> HttlClusterExtractedResponse<T>[] extract(final HttlRequest request, final Class<T> clazz) {
		//submit
		RequestFuture<ExtractedResponse<T>>[] futures = new RequestFuture[senders.length];
		for (int i = 0; i < senders.length; i++) {
			final HttlSender sender = senders[i];
			final HttlRequest serverRequest = new HttlRequest(request, sender.getConfig());
			Callable<ExtractedResponse<T>> callable = new Callable<ExtractedResponse<T>>() {

				@Override
				public ExtractedResponse<T> call() throws Exception {
					return sender.extract(serverRequest, clazz);
				}
			};
			futures[i] = new RequestFuture<ExtractedResponse<T>>(serverRequest, executor.submit(callable));
		}
		return extract(futures);
	}

	public <T> HttlClusterExtractedResponse<T>[] extract(final HttlRequest request, final GenericType<T> generic) {
		//submit
		RequestFuture<ExtractedResponse<T>>[] futures = new RequestFuture[senders.length];
		for (int i = 0; i < senders.length; i++) {
			final HttlSender sender = senders[i];
			final HttlRequest serverRequest = new HttlRequest(request, sender.getConfig());
			Callable<ExtractedResponse<T>> callable = new Callable<ExtractedResponse<T>>() {

				@Override
				public ExtractedResponse<T> call() throws Exception {
					return sender.extract(serverRequest, generic);
				}
			};
			futures[i] = new RequestFuture<ExtractedResponse<T>>(serverRequest, executor.submit(callable));
		}
		return extract(futures);
	}

	public <T> HttlClusterExtractedResponse<T>[] extract(final HttlRequest request,
			final HttlResponseExtractor<T> extractor) {
		//submit
		RequestFuture<ExtractedResponse<T>>[] futures = new RequestFuture[senders.length];
		for (int i = 0; i < senders.length; i++) {
			final HttlSender sender = senders[i];
			final HttlRequest serverRequest = new HttlRequest(request, sender.getConfig());
			Callable<ExtractedResponse<T>> callable = new Callable<ExtractedResponse<T>>() {

				@Override
				public ExtractedResponse<T> call() throws Exception {
					return sender.extract(serverRequest, extractor);
				}
			};
			futures[i] = new RequestFuture<ExtractedResponse<T>>(serverRequest, executor.submit(callable));
		}
		return extract(futures);
	}

	static class RequestFuture<X> {

		private final HttlRequest request;

		private final Future<X> future;

		public RequestFuture(HttlRequest request, Future<X> future) {
			this.request = request;
			this.future = future;
		}

		public HttlRequest getRequest() {
			return request;
		}

		public Future<X> getFuture() {
			return future;
		}

	}

	private <T> HttlClusterExtractedResponse<T>[] extract(RequestFuture<ExtractedResponse<T>>[] futures) throws Error {
		//collect
		HttlClusterExtractedResponse<T>[] responses = new HttlClusterExtractedResponse[senders.length];
		for (int i = 0; i < futures.length; i++) {
			RequestFuture<ExtractedResponse<T>> requestFuture = futures[i];
			try {
				ExtractedResponse<T> extracted = requestFuture.getFuture().get(readTimeoutMillis, TimeUnit.MILLISECONDS);
				responses[i] = new HttlClusterExtractedResponse<T>(extracted);
			} catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof Exception) {
					responses[i] = new HttlClusterExtractedResponse<T>((Exception) cause, requestFuture.getRequest());
				} else {
					throw (Error) cause;
				}

			} catch (InterruptedException ix) {
				responses[i] = new HttlClusterExtractedResponse<T>(ix, requestFuture.getRequest());
			} catch (TimeoutException tx) {
				responses[i] = new HttlClusterExtractedResponse<T>(tx, requestFuture.getRequest());
			}
		}
		return responses;
	}

	public void execute(final HttlRequest request, final HttlResponseHandler handler) {
		RequestFuture<?>[] futures = new RequestFuture[senders.length];
		for (int i = 0; i < senders.length; i++) {
			final HttlSender sender = senders[i];
			final HttlRequest serverRequest = new HttlRequest(request, sender.getConfig());
			Runnable worker = new Runnable() {

				@Override
				public void run() {
					sender.execute(serverRequest, handler);
				}

			};
			futures[i] = new RequestFuture(serverRequest, executor.submit(worker));
		}
		//collect
		for (int i = 0; i < futures.length; i++) {
			try {
				futures[i].getFuture().get(readTimeoutMillis, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ix) {
				handler.onFailure(futures[i].getRequest(), ix);
			} catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof Exception) {
					handler.onFailure(futures[i].getRequest(), (Exception) cause);
				} else {
					throw (Error) cause;
				}
			} catch (TimeoutException tx) {
				handler.onFailure(futures[i].getRequest(), tx);
			}
		}
	}

	/**
	 * Plain old synchronous 
	 */
	public HttlClusterResponse[] execute(final HttlRequest request) {
		if (executor.isShutdown()) {
			throw new IllegalStateException("ExecutorService shutted down");
		}
		//submit
		RequestFuture<HttlResponse>[] futures = new RequestFuture[senders.length];
		for (int i = 0; i < senders.length; ++i) {
			final HttlSender sender = senders[i];
			final HttlRequest serverRequest = new HttlRequest(request, sender.getConfig());
			Callable<HttlResponse> worker = new Callable<HttlResponse>() {

				@Override
				public HttlResponse call() throws Exception {
					return sender.execute(serverRequest);
				}

			};
			futures[i] = new RequestFuture<HttlResponse>(serverRequest, executor.submit(worker));
		}
		//collect
		HttlClusterResponse[] responses = new HttlClusterResponse[senders.length];
		for (int i = 0; i < futures.length; i++) {
			try {
				HttlResponse httlResponse = futures[i].getFuture().get(readTimeoutMillis, TimeUnit.MILLISECONDS);
				responses[i] = new HttlClusterResponse(httlResponse);
			} catch (InterruptedException ix) {
				responses[i] = new HttlClusterResponse(ix, futures[i].getRequest());
			} catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof Exception) {
					responses[i] = new HttlClusterResponse((Exception) cause, futures[i].getRequest());
				} else {
					throw (Error) cause;
				}

			} catch (TimeoutException tx) {
				responses[i] = new HttlClusterResponse(tx, futures[i].getRequest());
			}
		}
		return responses;
	}

	@Override
	public synchronized void close() {
		executor.shutdown();
		for (HttlSender sender : senders) {
			try {
				sender.close();
			} catch (Exception x) {
				//ignore...
			}
		}

	}

	public BodylessRequestBuilder<?> GET(String path) {
		return new BodylessRequestBuilder(senderConfig, Method.GET, path);
	}

	public BodylessRequestBuilder<?> HEAD(String path) {
		return new BodylessRequestBuilder(senderConfig, Method.HEAD, path);
	}

	public BodylessRequestBuilder<?> TRACE(String path) {
		return new BodylessRequestBuilder(senderConfig, Method.TRACE, path);
	}

	public BodylessRequestBuilder<?> OPTIONS(String path) {
		return new BodylessRequestBuilder(senderConfig, Method.OPTIONS, path);
	}

	public BodylessRequestBuilder<?> DELETE(String path) {
		return new BodylessRequestBuilder(senderConfig, Method.DELETE, path);
	}

	public BodyfulRequestBuilder<?> POST(String path) {
		return new BodyfulRequestBuilder(senderConfig, Method.POST, path);
	}

	public BodyfulRequestBuilder<?> PUT(String path) {
		return new BodyfulRequestBuilder(senderConfig, Method.PUT, path);
	}

	public BodyfulRequestBuilder<?> PATCH(String path) {
		return new BodyfulRequestBuilder(senderConfig, Method.PATCH, path);
	}

	public static class BaseClusterResponse {

		private final Exception exception; //nullable

		private final HttlRequest httlRequest;

		public BaseClusterResponse(Exception exception, HttlRequest request) {
			this.exception = exception; //nullable

			if (request == null) {
				throw new IllegalArgumentException("Null HttlRequest");
			}
			this.httlRequest = request;
		}

		public Exception getException() {
			return exception;
		}

		public HttlRequest getHttlRequest() {
			return httlRequest;
		}

		public String getHostName() {
			return httlRequest.getSenderConfig().getUrl().getHost();
		}

	}

	public static class HttlClusterResponse extends BaseClusterResponse implements Closeable {

		private final HttlResponse httlResponse;

		public HttlClusterResponse(HttlResponse response) {
			super(null, response.getRequest());
			this.httlResponse = response;
		}

		public HttlClusterResponse(Exception exception, HttlRequest request) {
			super(exception, request);
			this.httlResponse = null;
		}

		public HttlResponse getHttlResponse() {
			return httlResponse;
		}

		@Override
		public String toString() {
			if (httlResponse != null) {
				return "HttlClusterResponse {" + getHttlRequest() + ", response=" + httlResponse + "}";
			} else {
				return "HttlClusterResponse {" + getHttlRequest() + ", exception=" + getException() + "}";
			}
		}

		@Override
		public void close() {
			if (httlResponse != null) {
				httlResponse.close();
			}
		}

	}

	public static class HttlClusterExtractedResponse<T> extends BaseClusterResponse {

		private final ExtractedResponse<T> extractedResponse;

		public HttlClusterExtractedResponse(Exception exception, HttlRequest request) {
			super(exception, request);
			this.extractedResponse = null;
		}

		public HttlClusterExtractedResponse(ExtractedResponse<T> response) {
			super(null, response.getResponse().getRequest());
			this.extractedResponse = response;
		}

		public ExtractedResponse<T> getExtractedResponse() {
			return extractedResponse;
		}

		@Override
		public String toString() {
			if (extractedResponse != null) {
				return "HttlClusterResponse {" + getHttlRequest() + ", response=" + extractedResponse + "}";
			} else {
				return "HttlClusterResponse {" + getHttlRequest() + ", exception=" + getException() + "}";
			}
		}
	}

	private static class NamingThreadFactory implements ThreadFactory {

		private final String namePrefix;

		public NamingThreadFactory(String namePrefix) {
			this.namePrefix = namePrefix;
		}

		private static AtomicInteger counter = new AtomicInteger();

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			thread.setName(namePrefix + counter.incrementAndGet());
			return thread;
		}
	};
}
