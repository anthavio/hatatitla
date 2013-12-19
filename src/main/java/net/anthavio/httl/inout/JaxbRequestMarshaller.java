package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
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

/**
 * 
 * @author martin.vanek
 *
 */
public class JaxbRequestMarshaller implements RequestBodyMarshaller {

	private static final Map<Class<?>, JAXBContext> cache = new HashMap<Class<?>, JAXBContext>();

	private final JAXBContext jaxbContext;

	private Map<String, Object> marshallerProperties;

	/**
	 * Without shared JAXB context
	 */
	public JaxbRequestMarshaller() {
		this.jaxbContext = null;
		this.marshallerProperties = null;
	}

	public JaxbRequestMarshaller(Map<String, Object> marshallerProperties) {
		this.jaxbContext = null;
		this.marshallerProperties = marshallerProperties;
	}

	/**
	 * With shared JAXB context
	 */
	public JaxbRequestMarshaller(JAXBContext context) {
		if (context == null) {
			throw new IllegalArgumentException("null jaxb context");
		}
		this.jaxbContext = context;
	}

	public JaxbRequestMarshaller(JAXBContext context, Map<String, Object> marshallerProperties) {
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
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {

		Class<?> clazz = requestBody.getClass();
		requestBody = getRoot(requestBody, clazz);

		try {
			JAXBContext jaxbContext = getJaxbContext(clazz);
			Marshaller marshaller = createMarshaller(jaxbContext);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, charset.name()); //override default encoding is any
			marshaller.marshal(requestBody, stream);
		} catch (JAXBException jaxbx) {
			throw new IllegalArgumentException("Jaxb marshalling failed for " + requestBody, jaxbx);
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
