package net.anthavio.httl.marshall;

import net.anthavio.httl.HttlMarshaller;
import net.anthavio.httl.HttlUnmarshaller;

/**
 * 
 * @author martin.vanek
 *
 */
public class Defaults {

	//private final Logger logger = LoggerFactory.getLogger(getClass());

	private static boolean isSimpleXml = isClassPresent("org.simpleframework.xml.core.Persister");

	private static boolean isJaxbXml = isClassPresent("javax.xml.bind.JAXBContext");

	private static boolean isJackson2 = isClassPresent("com.fasterxml.jackson.databind.ObjectMapper");

	private static boolean isJackson1 = isClassPresent("org.codehaus.jackson.map.ObjectMapper");

	private static boolean isGson = isClassPresent("com.google.gson.Gson");

	public static HttlUnmarshaller getDefaultUnmarshaller(String mediaType) {
		if (mediaType.equals("application/json")) {
			return getDefaultJsonUnmarshaller();
		} else if (mediaType.equals("application/xml")) {
			return getDefaultXmlUnmarshaller();
		}
		return null;
	}

	public static HttlMarshaller getDefaultMarshaller(String mediaType) {
		if (mediaType.equals("application/json")) {
			return getDefaultJsonMarshaller();
		} else if (mediaType.equals("application/xml")) {
			return getDefaultXmlMarshaller();
		}
		return null;
	}

	public static HttlMarshaller getDefaultJsonMarshaller() {
		if (isJackson2) {
			return new Jackson2Marshaller();
		} else if (isJackson1) {
			return new Jackson1Marshaller();
		} else if (isGson) {
			return new GsonMarshaller();
		}
		return null;
	}

	public static HttlUnmarshaller getDefaultJsonUnmarshaller() {
		if (isJackson2) {
			return new Jackson2Unmarshaller();
		} else if (isJackson1) {
			return new Jackson1Unmarshaller();
		} else if (isGson) {
			return new GsonUnmarshaller();
		}
		return null;
	}

	private static HttlMarshaller getDefaultXmlMarshaller() {
		if (isSimpleXml) {
			return new SimpleXmlMarshaller();
		} else if (isJaxbXml) {
			return new JaxbMarshaller();
		}
		return null;
	}

	public static HttlUnmarshaller getDefaultXmlUnmarshaller() {
		if (isSimpleXml) {
			return new SimpleXmlUnmarshaller();
		} else if (isJaxbXml) {
			return new JaxbUnmarshaller();
		}
		return null;
	}

	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException cnfx) {
			return false;
		}
	}

}
