package net.anthavio.httl;

import java.io.IOException;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlResponseExtractor<T> {

	/**
	 * @return extracted body of SenderResponse
	 */
	public abstract T extract(HttlResponse response) throws IOException;

	/**
	 * Aggregation of reponse and extracted body
	 * 
	 */
	public static class ExtractedResponse<T> {

		private final HttlResponse response;

		private final T body;

		public ExtractedResponse(HttlResponse response, T payload) {
			this.response = response;
			this.body = payload;
		}

		/**
		 * @return Recieved response
		 */
		public HttlResponse getResponse() {
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
			return String.valueOf(response + " " + body);
		}

	}
	/*
		public static class ReaderExtractor implements HttlResponseExtractor<Reader> {

			@Override
			public ReaderExtractor supports(HttlResponse response) {
				return this;
			}

			@Override
			public Reader extract(HttlResponse response) throws IOException {
				return response.getReader();
			}
		};

		public static class StreamExtractor implements HttlResponseExtractor<InputStream> {

			@Override
			public StreamExtractor supports(HttlResponse response) {
				return this;
			}

			@Override
			public InputStream extract(HttlResponse response) throws IOException {
				return response.getStream();
			}
		};
	*/
}
