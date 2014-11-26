package net.anthavio.httl.transport;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.HttlTransport;

import org.apache.http.ContentTooLongException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * https://hc.apache.org/httpcomponents-asyncclient-dev/examples.html
 * 
 * @author martin.vanek
 *
 */
public class ApacheAsyncTransport implements HttlTransport<ApacheAsyncResponse> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final CloseableHttpAsyncClient client;

	private final HttpHost target;

	private final ApacheAsyncConfig config;

	public ApacheAsyncTransport(ApacheAsyncConfig config) {
		this.config = config;
		URL url = config.getUrl();
		this.target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

		HttpAsyncClientBuilder builder = HttpAsyncClients.custom();
		//TODO configure all the crap
		this.client = builder.build();
		this.client.start();
	}

	@Override
	public void close() {
		try {
			client.close();
		} catch (Exception x) {
			log.warn("Closing Apache HttpAsyncClient failed", x);
		}
	}

	@Override
	public ApacheAsyncConfig getConfig() {
		return config;
	}

	@Override
	public ApacheAsyncResponse call(HttlRequest httlRequest) throws IOException {
		HttlTransportCallback<ApacheAsyncResponse> callback = new TrackingCallback<ApacheAsyncResponse>();
		Future<ApacheAsyncResponse> future = call(httlRequest, callback);
		ApacheAsyncResponse response;
		try {
			response = future.get();
		} catch (InterruptedException ix) {
			throw new HttlRequestException(ix);
		} catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof Exception) {
				throw new HttlRequestException((Exception) cause);
			} else {
				throw new HttlRequestException(ex);//java.lang.Error!
			}
		}
		return response;
	}

	@Override
	public Future<ApacheAsyncResponse> call(final HttlRequest httlRequest,
			final HttlTransportCallback<ApacheAsyncResponse> httlCallback) {

		HttpRequest request;
		try {
			request = HttpClient4Transport.convert(httlRequest);
		} catch (IOException iox) {
			throw new HttlRequestException(iox);
		}
		HttpAsyncRequestProducer requestProducer = new CallbackingRequestProducer(target, request, httlRequest,
				httlCallback);

		//ZeroCopyConsumer,BasicAsyncResponseConsumer
		HttpAsyncResponseConsumer<ApacheAsyncResponse> responseConsumer = new CustomResponseConsumer(httlRequest,
				httlCallback);

		FutureCallback<ApacheAsyncResponse> callback = new FutureCallback<ApacheAsyncResponse>() {

			@Override
			public void failed(Exception ex) {
				System.out.println("failed " + ex);
			}

			@Override
			public void completed(ApacheAsyncResponse result) {
				System.out.println("completed " + result);
			}

			@Override
			public void cancelled() {
				System.out.println("cancelled"); //user can cancel from returned Future
			}
		};
		Future<ApacheAsyncResponse> future = client.execute(requestProducer, responseConsumer, callback);
		return future;

	}

	/**
	 * Pimped BasicAsyncResponseConsumer
	 * 
	 * @author martin.vanek
	 *
	 */
	class CustomResponseConsumer extends AbstractAsyncResponseConsumer<ApacheAsyncResponse> {

		private final HttlRequest httlRequest;

		private final HttlTransportCallback<ApacheAsyncResponse> httlCallback;

		private volatile HttpResponse response;
		private volatile SimpleInputBuffer buf;
		private ApacheAsyncResponse httlResponse;

		public CustomResponseConsumer(HttlRequest httlRequest, HttlTransportCallback<ApacheAsyncResponse> httlCallback) {
			this.httlRequest = httlRequest;
			this.httlCallback = httlCallback;
		}

		@Override
		protected void onResponseReceived(final HttpResponse response) throws IOException {
			this.response = response;
			StatusLine statusLine = response.getStatusLine();
			httlCallback.onResponseStatus(httlRequest, statusLine.getStatusCode(), statusLine.getReasonPhrase());

			Multival<String> httlHeaders = new Multival<String>();
			Header[] allHeaders = response.getAllHeaders();
			for (Header header : allHeaders) {
				httlHeaders.add(header.getName(), header.getValue());
			}

			httlResponse = new ApacheAsyncResponse(httlRequest, statusLine.getStatusCode(), statusLine.getReasonPhrase(),
					httlHeaders, response);
			httlCallback.onResponseHeaders(httlResponse);
		}

		@Override
		protected void onEntityEnclosed(final HttpEntity entity, final ContentType contentType) throws IOException {
			long len = entity.getContentLength();
			if (len > Integer.MAX_VALUE) {
				throw new ContentTooLongException("Entity content is too long: " + len);
			}
			if (len < 0) {
				len = 4096;
			}
			this.buf = new SimpleInputBuffer((int) len, new HeapByteBufferAllocator());
			this.response.setEntity(new ContentBufferEntity(entity, this.buf));

		}

		@Override
		protected void onContentReceived(final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
			Asserts.notNull(this.buf, "Content buffer");
			this.buf.consumeContent(decoder);
		}

		@Override
		protected void releaseResources() {
			this.response = null;
			this.buf = null;
		}

		@Override
		protected ApacheAsyncResponse buildResult(HttpContext context) {
			Exception exception = getException();
			if (exception == null) {
				httlCallback.onResponsePayload(httlResponse, httlResponse);
			} else {
				httlCallback.onResponsePayloadFailure(httlResponse, exception);
			}
			return httlResponse;
		}
	}

	/**
	 * Simple BasicAsyncRequestProducer with events routed into HttlTransportCallback
	 * 
	 * @author martin.vanek
	 *
	 */
	class CallbackingRequestProducer extends BasicAsyncRequestProducer {

		private final HttlTransportCallback<?> httlCallback;

		private final HttlRequest httlRequest;

		public CallbackingRequestProducer(HttpHost target, HttpRequest request, HttlRequest httlRequest,
				HttlTransportCallback<?> httlCallback) {
			super(target, request);
			this.httlRequest = httlRequest;
			this.httlCallback = httlCallback;
		}

		@Override
		public void requestCompleted(HttpContext context) {
			//produced, not really sent...
			this.httlCallback.onRequestCompleted(httlRequest);
		}

		@Override
		public void failed(Exception ex) {
			httlCallback.onRequestFailure(httlRequest, ex);
		}

	}
}
