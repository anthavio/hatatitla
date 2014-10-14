package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequestException;

/**
 * 
 * @author martin.vanek
 *
 */
public class JaxbMarshaller implements HttlBodyMarshaller {

	private static final Map<Class<?>, JAXBContext> cache = new HashMap<Class<?>, JAXBContext>();

	private final JAXBContext jaxbContext;

	private Map<String, Object> marshallerProperties;

	/**
	 * Without shared JAXB context
	 */
	public JaxbMarshaller() {
		this.jaxbContext = null;
		this.marshallerProperties = null;
	}

	public JaxbMarshaller(Map<String, Object> marshallerProperties) {
		this.jaxbContext = null;
		this.marshallerProperties = marshallerProperties;
	}

	/**
	 * With shared JAXB context
	 */
	public JaxbMarshaller(JAXBContext context) {
		if (context == null) {
			throw new IllegalArgumentException("null jaxb context");
		}
		this.jaxbContext = context;
	}

	public JaxbMarshaller(JAXBContext context, Map<String, Object> marshallerProperties) {
		this(context);
		this.marshallerProperties = marshallerProperties;
	}

	/**
	 * @return shared JAXB context
	 */
	public JAXBContext getJaxbContext() {
		return jaxbContext;
	}

	public Map<String, Object> getMarshallerProperties() {
		return marshallerProperties;
	}

	public void setMarshallerProperties(Map<String, Object> marshallerProperties) {
		this.marshallerProperties = marshallerProperties;
	}

	@Override
	public void marshall(Object payload, String mediaType, String charset, OutputStream stream) throws IOException {
		if (!mediaType.contains("xml")) {
			throw new HttlRequestException("Cannot mashall into " + mediaType);
		}
		write(payload, stream, charset);
	}

	public void write(Object payload, OutputStream stream, String charset) throws IOException {

		Class<?> clazz = payload.getClass();
		payload = getRoot(payload, clazz);

		try {
			JAXBContext jaxbContext = getJaxbContext(clazz);
			Marshaller marshaller = createMarshaller(jaxbContext);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, charset); //override default encoding is any
			marshaller.marshal(payload, stream);
		} catch (JAXBException jaxbx) {
			throw new IllegalArgumentException("Jaxb marshalling failed for " + payload, jaxbx);
		}

	}

	public String marshall(Object requestBody) throws IOException {
		Class<?> clazz = requestBody.getClass();
		requestBody = getRoot(requestBody, clazz);

		try {
			JAXBContext jaxbContext = getJaxbContext(clazz);
			Marshaller marshaller = createMarshaller(jaxbContext);
			StringWriter swriter = new StringWriter();
			marshaller.marshal(requestBody, swriter);
			return swriter.toString();
		} catch (JAXBException jaxbx) {
			throw new IllegalArgumentException("Jaxb marshalling failed for " + requestBody, jaxbx);
		}
	}

	private Object getRoot(Object requestBody, Class<?> clazz) {
		if (clazz.isAnnotationPresent(XmlRootElement.class) || clazz.isAnnotationPresent(XmlType.class)) {
			return requestBody;
		} else {
			//http://java.dzone.com/articles/jaxb-no-annotations-required
			QName qname = new QName(clazz.getSimpleName().toLowerCase());
			return new JAXBElement(qname, clazz, requestBody); //jaxb element becomes root			
		}
	}

	private JAXBContext getJaxbContext(Class<?> clazz) throws JAXBException {
		JAXBContext jaxbContext;
		if (this.jaxbContext != null) {
			jaxbContext = this.jaxbContext;
		} else {
			//without configured context - create single class context
			jaxbContext = cache.get(clazz);
			if (jaxbContext == null) {
				jaxbContext = JAXBContext.newInstance(clazz);
				cache.put(clazz, jaxbContext);
			}
		}
		return jaxbContext;
	}

	private Marshaller createMarshaller(JAXBContext jaxbContext) throws JAXBException, PropertyException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		if (this.marshallerProperties != null) {
			//marshaller.setProperty(Marshaller.JAXB_ENCODING, "utf-8");
			//marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			Set<Entry<String, Object>> entrySet = this.marshallerProperties.entrySet();
			for (Entry<String, Object> entry : entrySet) {
				marshaller.setProperty(entry.getKey(), entry.getValue());
			}
		}
		return marshaller;
	}

	@Override
	public String toString() {
		return "JaxbRequestMarshaller [jaxbContext=" + jaxbContext + "]";
	}

}
