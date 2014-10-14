package net.anthavio.httl.util;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlBodyUnmarshaller;
import net.anthavio.httl.marshall.GsonMarshaller;
import net.anthavio.httl.marshall.GsonUnmarshaller;
import net.anthavio.httl.marshall.Jackson1Marshaller;
import net.anthavio.httl.marshall.Jackson1Unmarshaller;
import net.anthavio.httl.marshall.Jackson2Marshaller;
import net.anthavio.httl.marshall.Jackson2Unmarshaller;
import net.anthavio.httl.marshall.JaxbMarshaller;
import net.anthavio.httl.marshall.JaxbUnmarshaller;
import net.anthavio.httl.marshall.SimpleXmlMarshaller;
import net.anthavio.httl.marshall.SimpleXmlUnmarshaller;

/**
 * 
 * @author martin.vanek
 *
 */
public class OptionalLibs {

	//private final Logger logger = LoggerFactory.getLogger(getClass());

	public static boolean isSimpleXml = isClassPresent("org.simpleframework.xml.core.Persister");

	public static boolean isJaxbXml = isClassPresent("javax.xml.bind.JAXBContext");

	public static boolean isJackson2 = isClassPresent("com.fasterxml.jackson.databind.ObjectMapper");

	public static boolean isJackson1 = isClassPresent("org.codehaus.jackson.map.ObjectMapper");

	public static boolean isGson = isClassPresent("com.google.gson.Gson");

	public static boolean isHttpClient3 = isClassPresent("org.apache.commons.httpclient.HttpClient");

	public static boolean isHttpClient4 = isClassPresent("org.apache.http.client.HttpClient");

	/*
	private static boolean isHttpClient41 = isClassPresent("org.apache.http.impl.client.ContentEncodingHttpClient");
	private static boolean isHttpClient42 = isClassPresent("org.apache.http.impl.client.SystemDefaultHttpClient");
	private static boolean isHttpClient43 = isClassPresent("org.apache.http.impl.client.CloseableHttpClient");
	*/

	public static HttlBodyUnmarshaller findUnmarshaller(String mediaType) {
		if (mediaType.contains("json")) {
			return findJsonUnmarshaller();
		} else if (mediaType.contains("xml")) {
			return findXmlUnmarshaller();
		}
		return null;
	}

	public static HttlBodyMarshaller findMarshaller(String mediaType) {
		if (mediaType.contains("json")) {
			return findJsonMarshaller();
		} else if (mediaType.contains("xml")) {
			return findXmlMarshaller();
		}
		return null;
	}

	public static HttlBodyMarshaller findJsonMarshaller() {
		if (isJackson2) {
			return new Jackson2Marshaller();
		} else if (isJackson1) {
			return new Jackson1Marshaller();
		} else if (isGson) {
			return new GsonMarshaller();
		}
		return null;
	}

	public static HttlBodyUnmarshaller findJsonUnmarshaller() {
		if (isJackson2) {
			return new Jackson2Unmarshaller();
		} else if (isJackson1) {
			return new Jackson1Unmarshaller();
		} else if (isGson) {
			return new GsonUnmarshaller();
		}
		return null;
	}

	private static HttlBodyMarshaller findXmlMarshaller() {
		if (isSimpleXml) {
			return new SimpleXmlMarshaller();
		} else if (isJaxbXml) {
			return new JaxbMarshaller();
		}
		return null;
	}

	public static HttlBodyUnmarshaller findXmlUnmarshaller() {
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
