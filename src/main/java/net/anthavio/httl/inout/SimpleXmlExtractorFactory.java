package net.anthavio.httl.inout;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.SenderResponse;

import org.simpleframework.xml.core.Persister;

/**
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlExtractorFactory implements ResponseExtractorFactory {

	private final Map<Type, SimpleXmlResponseExtractor<?>> cache = new HashMap<Type, SimpleXmlResponseExtractor<?>>();

	private final Persister persister;

	public SimpleXmlExtractorFactory() {
		this.persister = new Persister();
	}

	/**
	 * @param persister externally created and configured persister
	 */
	public SimpleXmlExtractorFactory(Persister persister) {
		if (persister == null) {
			throw new IllegalArgumentException("persister is null");
		}
		this.persister = persister;
	}

	/**
	 * @return hackish access to cached extractors
	 */
	public Map<Type, SimpleXmlResponseExtractor<?>> getCache() {
		return cache;
	}

	/**
	 * @return shared persister
	 */
	public Persister getPersister() {
		return persister;
	}

	@Override
	public <T> SimpleXmlResponseExtractor<T> getExtractor(SenderResponse response, Class<T> resultType) {
		SimpleXmlResponseExtractor<T> extractor = (SimpleXmlResponseExtractor<T>) cache.get(resultType);
		if (extractor == null) {
			extractor = new SimpleXmlResponseExtractor<T>(resultType, persister);
			cache.put(resultType, extractor);
		}

		return extractor;
	}

	@Override
	public <T> SimpleXmlResponseExtractor<T> getExtractor(SenderResponse response, ParameterizedType resultType) {
		throw new UnsupportedOperationException("Persister can only read Class " + resultType);
	}

	@Override
	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Type resultType) {
		throw new UnsupportedOperationException("Persister can only read Class " + resultType);
	}

	@Override
	public String toString() {
		return "SimpleXmlExtractorFactory [persister=" + persister + ", cache=" + cache.size() + "]";
	}

}
