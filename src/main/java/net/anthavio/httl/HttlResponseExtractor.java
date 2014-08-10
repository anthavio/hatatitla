package net.anthavio.httl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlResponseExtractor<T> {

	public static StringExtractor STRING = new StringExtractor(200, 299);

	public HttlResponseExtractor<T> supports(HttlResponse response);

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

	/**
	 * Simple string extractor. 
	 */
	public static class StringExtractor implements HttlResponseExtractor<String> {

		private int httpMin;
		private int httpMax;

		public StringExtractor(int httpMin, int httpMax) {
			this.httpMin = httpMin;
			this.httpMax = httpMax;
		}

		@Override
		public StringExtractor supports(HttlResponse response) {
			return this;
		}

		@Override
		public String extract(HttlResponse response) throws IOException {
			if (response.getHttpStatusCode() > httpMax || response.getHttpStatusCode() < httpMin) {
				throw new HttlStatusException(response);
			} else {
				return HttpHeaderUtil.readAsString(response);
			}
		}

	};

	public static class BytesExtractor implements HttlResponseExtractor<byte[]> {

		private int httpMin;
		private int httpMax;

		public BytesExtractor(int httpMin, int httpMax) {
			this.httpMin = httpMin;
			this.httpMax = httpMax;
		}

		@Override
		public BytesExtractor supports(HttlResponse response) {
			return this;
		}

		@Override
		public byte[] extract(HttlResponse response) throws IOException {
			if (response.getHttpStatusCode() > httpMax || response.getHttpStatusCode() < httpMin) {
				throw new HttlStatusException(response);
			} else {
				return HttpHeaderUtil.readAsBytes(response);
			}
		}
	};

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

}
