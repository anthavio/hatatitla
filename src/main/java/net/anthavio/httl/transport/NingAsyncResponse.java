package net.anthavio.httl.transport;

import java.io.InputStream;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

/**
 * 
 * @author martin.vanek
 *
 */
public class NingAsyncResponse extends HttlResponse {

	private static final long serialVersionUID = 1L;

	private final HttpResponse response;

	public NingAsyncResponse(HttlRequest request, int httpCode, String message, Multival<String> headers,
			HttpResponse response) {
		super(request, httpCode, message, headers, null);
		this.response = response;
	}

	@Override
	public InputStream getStream() {
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			try {
				return entity.getContent();
			} catch (Exception x) {
				throw new HttlRequestException(x);
			}
		} else {
			throw new IllegalStateException("No HttpEntity in " + this);
		}
	}

	@Override
	public void close() {
		EntityUtils.consumeQuietly(response.getEntity());
	}

}
