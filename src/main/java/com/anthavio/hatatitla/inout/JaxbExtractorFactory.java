package com.anthavio.hatatitla.inout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;

/**
 * 
 * @author martin.vanek
 *
 */
public class JaxbExtractorFactory implements ResponseExtractorFactory {

	private final Map<Class<?>, JaxbResponseExctractor<?>> cache = new HashMap<Class<?>, JaxbResponseExctractor<?>>();

	private final JAXBContext jaxbContext;

	public JaxbExtractorFactory() {
		this.jaxbContext = null; //no shared jaxb context
	}

	public JaxbExtractorFactory(JAXBContext jaxbContext) {
		if (jaxbContext == null) {
			throw new IllegalArgumentException("JAXB context is null");
		}
		this.jaxbContext = jaxbContext;
	}

	public Map<Class<?>, JaxbResponseExctractor<?>> getCache() {
		return cache;
	}

	public JAXBContext getJaxbContext() {
		return jaxbContext;
	}

	@Override
	public <T extends Serializable> JaxbResponseExctractor<T> getExtractor(Class<T> resultType) {
		JaxbResponseExctractor<T> extractor = (JaxbResponseExctractor<T>) cache.get(resultType);
		if (extractor == null) {
			if (this.jaxbContext != null) {
				extractor = new JaxbResponseExctractor<T>(resultType, jaxbContext);
			} else {
				extractor = new JaxbResponseExctractor<T>(resultType); //no shared jaxb context
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
