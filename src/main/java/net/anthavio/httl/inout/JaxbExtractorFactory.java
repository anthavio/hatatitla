package net.anthavio.httl.inout;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import net.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class JaxbExtractorFactory implements ResponseExtractorFactory {

	private final Map<Type, JaxbResponseExtractor<?>> cache = new HashMap<Type, JaxbResponseExtractor<?>>();

	private final JAXBContext jaxbContext;

	public JaxbExtractorFactory() {
		this.jaxbContext = null; //no shared jaxb context
	}

	/**
	 * With external jaxb context
	 */
	public JaxbExtractorFactory(JAXBContext jaxbContext) {
		if (jaxbContext == null) {
			throw new IllegalArgumentException("JAXB context is null");
		}
		this.jaxbContext = jaxbContext;
	}

	/**
	 * @return hackish access to cached extractors
	 */
	public Map<Type, JaxbResponseExtractor<?>> getCache() {
		return cache;
	}

	/**
	 * @return shared jaxb context or null
	 */
	public JAXBContext getJaxbContext() {
		return jaxbContext;
	}

	@Override
	public <T> JaxbResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
		JaxbResponseExtractor<T> extractor = (JaxbResponseExtractor<T>) cache.get(resultType);
		if (extractor == null) {
			if (this.jaxbContext != null) {
				extractor = new JaxbResponseExtractor<T>(resultType, jaxbContext);
			} else {
				extractor = new JaxbResponseExtractor<T>(resultType); //no shared jaxb context
			}
			cache.put(resultType, extractor);
		}
		return extractor;
	}

	@Override
	public <T> JaxbResponseExtractor<T> getExtractor(SenderResponse response, ParameterizedType resultType) {
		throw new UnsupportedOperationException("JAXBContext can be created only for Class " + resultType);
	}

	@Override
	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Type resultType) {
		throw new UnsupportedOperationException("JAXBContext can be created only for Class " + resultType);
	}

	@Override
	public String toString() {
		return "JaxbExtractorFactory [jaxbContext=" + jaxbContext + ", cache=" + cache.size() + "]";
	}

}
