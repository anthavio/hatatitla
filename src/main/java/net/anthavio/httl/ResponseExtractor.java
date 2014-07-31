package net.anthavio.httl;

import java.io.IOException;

import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractor<T> {

	public boolean support(HttlResponse response);

	/**
	 * @return extracted body of SenderResponse
	 */
	public abstract T extract(HttlResponse response) throws IOException;

	/**
	 * Simple string extractor. 
	 */
	public static final ResponseExtractor<String> STRING = new ResponseExtractor<String>() {

		@Override
		public boolean support(HttlResponse response) {
			return true;
		}

		@Override
		public String extract(HttlResponse response) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsString(response);
			} else {
				throw new ResponseStatusException(response);
			}
		}

	};

	public static final ResponseExtractor<byte[]> BYTES = new ResponseExtractor<byte[]>() {
		@Override
		public boolean support(HttlResponse response) {
			return true;
		}

		@Override
		public byte[] extract(HttlResponse response) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsBytes(response);
			} else {
				throw new ResponseStatusException(response);
			}
		}
	};

	/**
	 * Aggregation of reponse and extracted body
	 * 
	 */
	public static class ExtractedResponse<T> {

		private final HttlResponse response;

		private final T body;

		public ExtractedResponse(HttlResponse response, T body) {
			this.response = response;
			this.body = body;
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
			return String.valueOf(body);
		}

	}

}
