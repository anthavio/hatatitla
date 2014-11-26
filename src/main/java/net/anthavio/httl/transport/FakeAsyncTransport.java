package net.anthavio.httl.transport;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.Cutils;

/**
 * https://code.google.com/p/json-simple/wiki/DecodingExamples#Example_5_-_Stoppable_SAX-like_content_handler
 * org.json.simple.parser.JSONParser
 * 
 * @author martin.vanek
 *
 */
public abstract class FakeAsyncTransport implements HttlTransport<HttlResponse> {

	@Override
	public Future<HttlResponse> call(HttlRequest request, HttlTransportCallback<HttlResponse> callback) {
		if (callback == null) {
			throw new IllegalArgumentException("Null callback");
		}
		HttlResponse response = null;
		try {

			try {
				//blocking execution
				response = call(request);
				callback.onRequestCompleted(request);

			} catch (HttlRequestException hrx) {
				callback.onRequestFailure(request, hrx);
				return null;
			} catch (ConnectException cx) {
				callback.onRequestFailure(request, cx);
				return null;
			} catch (SSLException sslx) {
				callback.onRequestFailure(request, sslx);
				return null;
			} catch (SocketTimeoutException stx) {
				callback.onResponseFailure(request, stx);
				return null;
			} catch (Exception x) {
				//For others blame response
				callback.onResponseFailure(request, x);
				return null;
			}
			callback.onResponseStatus(request, response.getHttpStatusCode(), response.getHttpStatusMessage());
			callback.onResponseHeaders(response);
			callback.onResponsePayload(response, response);

			return new FakeFuture<HttlResponse>(response);

		} finally {
			Cutils.close(response);
		}
	}

	public static final class FakeFuture<T> implements Future<T> {

		public final T value;

		public FakeFuture(T value) {
			this.value = value;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return true;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			return value;
		}

		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return value;
		}

	}
}
