package net.anthavio.httl.inout;

import java.io.IOException;

import net.anthavio.httl.SenderResponse;


/**
 * 
 * @author martin.vanek
 *
 */
public abstract class ResponseBodyExtractor<T> {

	/**
	 * 
	 */
	public boolean isExtractable(SenderResponse response) {
		return response.getHttpStatusCode() < 300;
	}

	/**
	 * @return extracted body of SenderResponse
	 */
	public abstract T extract(SenderResponse response) throws IOException;

	/**
	 * Aggregation of reponse and extracted body
	 * 
	 * @author martin.vanek
	 * 
	 */
	public static class ExtractedBodyResponse<T> {

		private final SenderResponse response;

		private final T body;

		public ExtractedBodyResponse(SenderResponse response, T body) {
			this.response = response;
			this.body = body;
		}

		/**
		 * @return Recieved response
		 */
		public SenderResponse getResponse() {
			return response;
		}

		/**
		 * @return Extracted response body
		 */
		public T getBody() {
			return body;
		}

		@Override
		public String toString() {
			return String.valueOf(body);
		}

	}

}
