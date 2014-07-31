package net.anthavio.httl.inout;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import net.anthavio.httl.ResponseUnmarshaller.ConfigurableUnmarshaller;
import net.anthavio.httl.HttlResponse;

/**
 * Xml -> Java JAXB based ResponseExtractor
 * 
 * HttpSender.extract(new GetRequest("/my_entity.xml"), new JaxbResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class JaxbUnmarshaller extends ConfigurableUnmarshaller {

	private static final Map<Class<?>, JAXBContext> cache = new HashMap<Class<?>, JAXBContext>();

	private final JAXBContext jaxbContext;

	public JaxbUnmarshaller() {
		this((JAXBContext) null);
	}

	public JaxbUnmarshaller(JAXBContext jaxbContext) {
		this(jaxbContext, "application/xml");
	}

	public JaxbUnmarshaller(JAXBContext jaxbContext, String mediaType) {
		this(jaxbContext, mediaType, 200, 299);
	}

	public JaxbUnmarshaller(JAXBContext jaxbContext, String mediaType, int minHttpStatus, int maxHttpStatus) {
		super(mediaType, minHttpStatus, maxHttpStatus);
		this.jaxbContext = jaxbContext;
	}

	public JaxbUnmarshaller(Class<?>... classesToBeBound) throws JAXBException {
		this(JAXBContext.newInstance(classesToBeBound));
	}

	public JaxbUnmarshaller(String mediaType, int minHttpStatus, int maxHttpStatus, Class<?>... classesToBeBound)
			throws JAXBException {
		this(JAXBContext.newInstance(classesToBeBound), mediaType, minHttpStatus, maxHttpStatus);
	}

	protected Unmarshaller createUnmarshaller(JAXBContext jaxbContext, HttlResponse response) throws JAXBException {
		return jaxbContext.createUnmarshaller();
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

	@Override
	public Object unmarshall(HttlResponse response, Type resultType) throws IOException {
		if (!(resultType instanceof Class)) {
			throw new IllegalArgumentException("JAXB can only read Class " + resultType);
		}
		try {
			//StreamSource source = new StreamSource(new InputStreamReader(response.getStream(), response.getCharset()));
			Unmarshaller unmarshaller = createUnmarshaller(getJaxbContext((Class<?>) resultType), response);
			Object object = unmarshaller.unmarshal(response.getReader());
			if (object instanceof JAXBElement<?>) {
				return ((JAXBElement<?>) object).getValue();
			}
			return object;
			//return resultType.cast(object);
		} catch (JAXBException jaxbx) {
			throw new IllegalArgumentException(jaxbx.getMessage(), jaxbx);
		}
	}
}
