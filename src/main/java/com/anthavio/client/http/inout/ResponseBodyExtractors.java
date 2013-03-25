package com.anthavio.client.http.inout;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.anthavio.client.http.Cutils;
import com.anthavio.client.http.HttpHeaderUtil;
import com.anthavio.client.http.SenderResponse;

/**
 * Storage for ResponseExtractor
 * 
 * @author martin.vanek
 *
 */
public class ResponseBodyExtractors {

	private Map<String, ResponseExtractorFactory> factories = new HashMap<String, ResponseExtractorFactory>();

	public ResponseBodyExtractors() {

		JaxbExtractorFactory jaxbFactory = new JaxbExtractorFactory();
		this.factories.put("application/xml", jaxbFactory);
		this.factories.put("text/xml", jaxbFactory);

		//Jackson support is optional
		try {
			Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
			factories.put("application/json", new Jackson2ExtractorFactory());
		} catch (ClassNotFoundException cnf) {
			try {
				Class.forName("org.codehaus.jackson.map.ObjectMapper");
				factories.put("application/json", new Jackson1ExtractorFactory());
			} catch (ClassNotFoundException cnf2) {
			}
		}
	}

	public ResponseExtractorFactory getExtractorFactory(String mimeType) {
		return factories.get(mimeType);
	}

	public void setExtractorFactory(ResponseExtractorFactory extractorFactory, String mimeType, int... httpStatus) {
		if (extractorFactory == null) {
			throw new IllegalArgumentException("extractor factory is null");
		}
		//XXX error response extractor
		factories.put(mimeType, extractorFactory);
	}

	public static final ResponseBodyExtractor<String> STRING = new ResponseBodyExtractor<String>() {

		@Override
		public String extract(SenderResponse response) throws IOException {
			return HttpHeaderUtil.readAsString(response);
		}
	};

	public static final ResponseBodyExtractor<byte[]> BYTES = new ResponseBodyExtractor<byte[]>() {

		@Override
		public byte[] extract(SenderResponse response) throws IOException {
			return HttpHeaderUtil.readAsBytes(response);
		}
	};

	public <T extends Serializable> T extract(SenderResponse response, Class<T> clazz) throws IOException {
		String contentType = response.getFirstHeader("Content-Type");
		ResponseBodyExtractor<?> extractor = getExtractor(contentType, clazz);
		if (extractor == null) {
			throw new IllegalArgumentException("No extractor found for class " + clazz.getName() + " and Content-Type "
					+ contentType);
		}
		return (T) extractor.extract(response);
	}

	public <T extends Serializable> ResponseBodyExtractor<T> getExtractor(String contentType, Class<T> clazz) {
		// Ignore Content-Type for String or Byte Array result
		if (clazz.equals(String.class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.STRING;
		} else if (clazz.equals(byte[].class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.BYTES;
		}

		if (Cutils.isEmpty(contentType)) {
			throw new IllegalArgumentException("Content-Type header not found");
		}

		String mimeType = (String) HttpHeaderUtil.splitContentType(contentType, null)[0];
		ResponseExtractorFactory extractorFactory = factories.get(mimeType);
		if (extractorFactory == null) {
			throw new IllegalArgumentException("No extractor factory found for mime type " + mimeType);
		}
		ResponseBodyExtractor<T> extractor = extractorFactory.getExtractor(clazz);
		return extractor;
	}

}
