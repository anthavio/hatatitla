package net.anthavio.httl.transport;

import java.io.IOException;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.TransportBuilder.BaseTransBuilder;

import com.ning.http.client.AsyncHttpClient;

/**
 * https://github.com/AsyncHttpClient/async-http-client
 * 
 * @author martin.vanek
 *
 */
public class NingTransport implements HttlTransport {

	AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

	public NingTransport(NingConfig config) {

	}

	@Override
	public HttlResponse call(HttlRequest request) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void call(HttlRequest request, HttlTransportCallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public BaseTransBuilder<?> getConfig() {
		// TODO Auto-generated method stub
		return null;
	}

}
