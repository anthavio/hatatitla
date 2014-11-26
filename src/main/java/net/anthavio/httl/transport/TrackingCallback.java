package net.anthavio.httl.transport;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlTransport.HttlTransportCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class TrackingCallback<T> implements HttlTransportCallback<T> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void onRequestCompleted(HttlRequest request) {
		logger.debug("onRequestCompleted " + request);
	}

	@Override
	public void onRequestFailure(HttlRequest request, Exception exception) {
		logger.debug("onRequestFailure " + exception + " " + request);
	}

	@Override
	public void onResponseFailure(HttlRequest request, Exception exception) {
		logger.debug("onResponseFailure " + exception + " " + request);
	}

	@Override
	public void onResponseStatus(HttlRequest request, int statusCode, String statusMessage) {
		logger.debug("onResponseStatus " + request + " " + statusCode + " " + statusCode);
	}

	@Override
	public void onResponseHeaders(HttlResponse response) {
		logger.debug("onResponseHeaders " + response);
	}

	@Override
	public void onResponsePayloadFailure(HttlResponse response, Exception exception) {
		logger.debug("onResponsePayloadFailure " + response);
	}

	@Override
	public void onResponsePayload(HttlResponse response, T internal) {
		logger.debug("onResponsePayload " + response + " " + internal);
	}

}
