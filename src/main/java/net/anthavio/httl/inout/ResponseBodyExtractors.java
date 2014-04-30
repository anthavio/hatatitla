package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.GenericType;
import net.anthavio.httl.util.HttpHeaderUtil;

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

		ResponseExtractorFactory factory = null;
		//First try SimplXml
		try {
			Class.forName("org.simpleframework.xml.core.Persister");
			factory = new SimpleXmlExtractorFactory();
			setExtractorFactory(factory, "text/xml");
			setExtractorFactory(factory, "application/xml");
		} catch (ClassNotFoundException cnfx) {
			//nothing
		}

		//Then try JAXB
		if (factory == null) {
			try {
				Class.forName("javax.xml.bind.JAXBContext");
				factory = new JaxbExtractorFactory();
				setExtractorFactory(factory, "text/xml");
				setExtractorFactory(factory, "application/xml");
			} catch (ClassNotFoundException cnf) {
				//nothing
			}
		}

		factory = null;
		//JSON support

		//First try Jackson 2
		try {
			Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
			factory = new Jackson2ExtractorFactory();
			setExtractorFactory(factory, "application/json");
		} catch (ClassNotFoundException cnf) {
			//nothing
		}

		//Then try Jackson 1
		if (factory == null) {
			try {
				Class.forName("org.codehaus.jackson.map.ObjectMapper");
				factory = new Jackson1ExtractorFactory();
				setExtractorFactory(factory, "application/json");
			} catch (ClassNotFoundException cnf) {
				//nothing
			}
		}

		//Then try Gson
		if (factory == null) {
			try {
				Class.forName("com.google.gson.Gson");
				factory = new GsonExtractorFactory();
				setExtractorFactory(factory, "application/json");
			} catch (ClassNotFoundException cnfx) {
				//nothing
			}
		}
	}

	public ResponseExtractorFactory getExtractorFactory(String mediaType) {
		return factories.get(mediaType);
	}

	public void setExtractorFactory(ResponseExtractorFactory extractorFactory, String mediaType) {
		if (extractorFactory == null) {
			throw new IllegalArgumentException("extractor factory is null");
		}
		if (Cutils.isBlank(mediaType)) {
			throw new IllegalArgumentException("media type is blank");
		}
		//logger.debug("Adding " + extractorFactory.getClass().getName() + " for " + mediaType);
		factories.put(mediaType, extractorFactory);
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

	public static final ResponseBodyExtractor<Reader> READER = new ResponseBodyExtractor<Reader>() {

		@Override
		public Reader extract(SenderResponse response) throws IOException {
			return response.getReader();
		}
	};

	public static final ResponseBodyExtractor<InputStream> STREAM = new ResponseBodyExtractor<InputStream>() {

		@Override
		public InputStream extract(SenderResponse response) throws IOException {
			return response.getStream();
		}
	};

	/**
	 * Extracts Response into desired resultType or fails miserably.
	 * This method does NOT close Response
	 */
	public <T> T extract(SenderResponse response, Class<T> resultType) throws IOException {
		String contentType = response.getFirstHeader("Content-Type");
		ResponseBodyExtractor<T> extractor = getExtractor(response, resultType);
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor not found for class " + resultType.getName() + " and Content-Type "
					+ contentType);
		}
		return (T) extractor.extract(response);
	}

	/**
	 * Extracts Response into desired resultType or fails miserably.
	 * This method does NOT close Response
	 */
	public <T> T extract(SenderResponse response, GenericType<T> typeReference) throws IOException {
		String contentType = response.getFirstHeader("Content-Type");
		ResponseBodyExtractor<T> extractor = getExtractor(response, typeReference);
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor not found for class " + typeReference + " and Content-Type "
					+ contentType);
		}
		return (T) extractor.extract(response);
	}

	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Class<T> clazz) {
		// Ignore Content-Type for String or Byte Array result
		if (clazz.equals(String.class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.STRING;
		} else if (clazz.equals(byte[].class)) {
			return (ResponseBodyExtractor<T>) ResponseBodyExtractors.BYTES;
		}

		ResponseExtractorFactory extractorFactory = getFactory(response, clazz);
		ResponseBodyExtractor<T> extractor = extractorFactory.getExtractor(response, clazz);
		if (extractor == null) {
			throw new IllegalStateException("ResponseExtractorFactory " + extractorFactory + " returned null");
		}
		return extractor;
	}

	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, GenericType<T> typeReference) {
		ResponseExtractorFactory extractorFactory = getFactory(response, typeReference.getParameterizedType());
		ResponseBodyExtractor<T> extractor = extractorFactory.getExtractor(response, typeReference.getParameterizedType());
		if (extractor == null) {
			throw new IllegalStateException("ResponseExtractorFactory " + extractorFactory + " returned null");
		}
		return extractor;
	}

	public ResponseExtractorFactory getFactory(SenderResponse response, Type type) {
		String contentType = response.getFirstHeader("Content-Type");
		if (Cutils.isEmpty(contentType)) {
			throw new IllegalArgumentException("Content-Type header not found");
		}
		String mediaType = HttpHeaderUtil.getMediaType(contentType);
		ResponseExtractorFactory extractorFactory = factories.get(mediaType);
		if (extractorFactory == null) {
			throw new IllegalArgumentException("ResponseExtractorFactory not found for " + mediaType + " to extract " + type);
		}
		return extractorFactory;
	}

}
