package net.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;

import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlTransport extends Closeable {

	/**
	 * Synchronous blocking execution
	 */
	public HttlResponse call(HttlRequest request) throws IOException;

	/**
	 * Asynchronous nio execution
	 */
	public void call(HttlRequest request, HttlTransportCallback callback);

	/**
	 * Redeclare Closeable to surpress IOException
	 */
	public void close();

	public BaseTransportBuilder<?> getConfig();

	/**
	 *  Callback for Asynchronous nio execution
	 */
	static interface HttlTransportCallback {

		/**
		 * Most probably connect timeouts, SSL handshake errors
		 */
		public void onRequestFailure(HttlRequest request, Exception exception);

		/**
		 * Request was sent successfully but nothing comes back (headers or status code)
		 */
		public void onResponseFailure(HttlRequest request, Exception exception);

		/**
		 * Happy happy happy!
		 */
		public void onResponse(HttlResponse response);

	}
}
