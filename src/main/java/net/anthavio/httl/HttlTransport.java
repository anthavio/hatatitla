package net.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlTransport<AF> extends Closeable {

	/**
	 * Synchronous blocking execution
	 */
	public HttlResponse call(HttlRequest request) throws IOException;

	/**
	 * Asynchronous nio execution
	 */
	public Future<AF> call(HttlRequest request, HttlTransportCallback<AF> callback);

	/**
	 * Redeclare Closeable to surpress IOException
	 */
	public void close();

	public BaseTransBuilder<?> getConfig();

	/**
	 *  Callback for Asynchronous nio execution
	 */
	public static interface HttlTransportCallback<AF> {

		/**
		 * Request is written successfully
		 */
		public void onRequestCompleted(HttlRequest request);

		/**
		 * Connect timeouts, SSL handshake errors
		 */
		public void onRequestFailure(HttlRequest request, Exception exception);

		/**
		 * Request was written successfully but nothing comes back (headers or status code)
		 */
		public void onResponseFailure(HttlRequest request, Exception exception);

		/**
		 * HTTP Response status line recieved
		 */
		public void onResponseStatus(HttlRequest request, int statusCode, String statusMessage);

		/**
		 * HTTP Response headers recieved
		 */
		public void onResponseHeaders(HttlResponse response);

		/**
		 * HTTP Response payload read/processing
		 */
		public void onResponsePayloadFailure(HttlResponse response, Exception exception);

		/**
		 * HTTP Response body recieved
		 */
		public void onResponsePayload(HttlResponse response, AF internal);

	}
}
