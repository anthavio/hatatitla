package com.anthavio.client.http.inout;

import java.io.IOException;
import java.io.Serializable;

import com.anthavio.client.http.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseBodyExtractor<T> {

	public T extract(SenderResponse response) throws IOException;

	//public T extract(SenderResponse response, Class<T> clazz) throws IOException;

	/**
	 * Used as wrapper of extracted response
	 * 
	 * @author martin.vanek
	 * 
	 */
	public static class ExtractedBodyResponse<T extends Serializable> {

		private final SenderResponse response;

		private final T body;

		public ExtractedBodyResponse(SenderResponse response, T body) {
			this.response = response;
			this.body = body;
		}

		public SenderResponse getResponse() {
			return response;
		}

		public T getBody() {
			return body;
		}

	}

}
