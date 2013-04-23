package com.anthavio.httl.inout;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import com.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class JaxbExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, JaxbResponseExtractor<?>> cache = new HashMap<Class<?>, JaxbResponseExtractor<?>>();

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
	public Map<Class<?>, JaxbResponseExtractor<?>> getCache() {
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
	public String toString() {
		return "JaxbExtractorFactory [jaxbContext=" + jaxbContext + ", cache=" + cache.size() + "]";
	}

}
