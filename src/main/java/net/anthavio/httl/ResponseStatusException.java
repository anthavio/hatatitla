package net.anthavio.httl;

import java.io.IOException;

import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * Exception including response http status code
 * 
 * @author martin.vanek
 *
 */
public class ResponseStatusException extends HttlException {

	private static final long serialVersionUID = 1L;

	private final HttlResponse response;

	private final String responseBody;

	public ResponseStatusException(HttlResponse response) {
		super(response.getHttpStatusCode() + " " + response.getHttpStatusMessage());
		this.response = response;
		try {
			this.responseBody = HttpHeaderUtil.readAsString(response);
		} catch (IOException iox) {
			//XXX maybe log some warning...
			throw new ResponseProcessingException(iox);
		} finally {
			response.close();
		}
	}

	public HttlResponse getResponse() {
		return response;
	}

	public String getResponseBody() {
		return responseBody;
	}

}
