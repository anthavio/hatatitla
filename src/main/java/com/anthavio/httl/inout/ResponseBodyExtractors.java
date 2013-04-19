package com.anthavio.httl.inout;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.util.Cutils;
import com.anthavio.httl.util.HttpHeaderUtil;

/**
 * Storage for ResponseExtractor
 * 
 * @author martin.vanek
 *
 */
public class ResponseBodyExtractors {

	private Map<String, ResponseExtractorFactory> factories = new HashMap<String, ResponseExtractorFactory>();

	public ResponseBodyExtractors() {
		//extractors.add(new ExtractorEntry<String>(STRING));
		//extractors.add(new ExtractorEntry<byte[]>(BYTES));

		JaxbExtractorFactory jaxbFactory = new JaxbExtractorFactory();

		try {
			Class.forName("javax.xml.bind.JAXBContext");
			this.factories.put("application/xml", jaxbFactory);
			this.factories.put("text/xml", jaxbFactory);
		} catch (ClassNotFoundException cnf) {
			//no xml support then
		}

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

	public void setExtractorFactory(ResponseExtractorFactory extractorFactory, String mimeType) {
		if (extractorFactory == null) {
			throw new IllegalArgumentException("extractor factory is null");
		}
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

	/**
	 * Extracts Response into desired resultType or fails miserably.
	 * This method does NOT close Response
	 */
	public <T> T extract(SenderResponse response, Class<T> resultType) throws IOException {
		String contentType = response.getFirstHeader("Content-Type");
		ResponseBodyExtractor<?> extractor = getExtractor(contentType, response, resultType);
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor not found for class " + resultType.getName() + " and Content-Type "
					+ contentType);
		}
		return (T) extractor.extract(response);
	}

	public <T> ResponseBodyExtractor<T> getExtractor(String contentType, SenderResponse response, Class<T> clazz) {
		// Ignore Content-Type for String or Byte Array result
		if (clazz.equals(String.class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.STRING;
		} else if (clazz.equals(byte[].class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.BYTES;
		}

		if (Cutils.isEmpty(contentType)) {
			throw new IllegalArgumentException("Content-Type header not found");
		}

		String mimeType = HttpHeaderUtil.getMediaType(contentType);
		ResponseExtractorFactory extractorFactory = factories.get(mimeType);
		if (extractorFactory == null) {
			throw new IllegalArgumentException("Extractor factory not found for " + mimeType);
		}
		ResponseBodyExtractor<T> extractor = extractorFactory.getExtractor(response, clazz);
		return extractor;
	}

}
