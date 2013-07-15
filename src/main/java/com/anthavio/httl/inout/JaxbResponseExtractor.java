package com.anthavio.httl.inout;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.anthavio.httl.SenderResponse;

/**
 * Xml -> Java JAXB based ResponseExtractor
 * 
 * HttpSender.extract(new GetRequest("/my_entity.xml"), new JaxbResponseExctractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class JaxbResponseExtractor<T> extends ResponseBodyExtractor<T> {

	//primitive cache for JAXBContexts 
	private static Map<Class<?>, JAXBContext> cache = new HashMap<Class<?>, JAXBContext>();

	private final Class<T> resultType;

	private final JAXBContext jaxbContext;

	/**
	 * Creates single class JAXB context
	 */
	public JaxbResponseExtractor(Class<T> resultType) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		try {
			JAXBContext jaxbContext = cache.get(resultType);
			if (jaxbContext == null) {
				jaxbContext = JAXBContext.newInstance(resultType);
				cache.put(resultType, jaxbContext);
			}
			this.jaxbContext = jaxbContext;

		} catch (JAXBException ex) {
			throw new IllegalArgumentException(ex.getMessage(), ex);
		}
	}

	/**
	 * If resultType class is part of bigger jaxbContext...
	 */
	public JaxbResponseExtractor(Class<T> resultType, JAXBContext jaxbContext) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		if (jaxbContext == null) {
			throw new IllegalArgumentException("jaxbContext is null");
		}
		this.jaxbContext = jaxbContext;

	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			//StreamSource source = new StreamSource(new InputStreamReader(response.getStream(), response.getCharset()));
			Unmarshaller unmarshaller = this.jaxbContext.createUnmarshaller();
			object = unmarshaller.unmarshal(response.getReader());
			if (object instanceof JAXBElement<?>) {
				return ((JAXBElement<T>) object).getValue();
			}
			return (T) object;
			//return resultType.cast(object);
		} catch (JAXBException jaxbx) {
			throw new IllegalArgumentException(jaxbx.getMessage(), jaxbx);
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + resultType.getName() + " value: "
					+ object;
			throw new IllegalArgumentException(message, ccx);
		}
	}
}