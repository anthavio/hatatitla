package net.anthavio.httl.transport;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SyncAsyncTransport implements HttlTransport {

	@Override
	public void call(HttlRequest request, HttlTransportCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("Null callback");
		}
		HttlResponse response = null;
		try {

			try {
				//blocking execution
				response = call(request);

			} catch (HttlRequestException hrx) {
				callback.onRequestFailure(request, hrx);

			} catch (ConnectException cx) {
				callback.onRequestFailure(request, cx);

			} catch (SSLException sslx) {
				callback.onRequestFailure(request, sslx);

			} catch (SocketTimeoutException stx) {
				callback.onResponseFailure(request, stx);

			} catch (Exception x) {
				//For others blame response
				callback.onResponseFailure(request, x);
			}

			callback.onResponse(response); // 

		} finally {
			Cutils.close(response);
		}
	}
}
