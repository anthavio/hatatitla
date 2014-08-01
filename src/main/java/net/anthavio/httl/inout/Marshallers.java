package net.anthavio.httl.inout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.util.Cutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storage for HttpSender's RequestBodyMarshallers. 
 * JAXB marshaller is allways installed for media types text/xml and application/xml
 * When Jackson is found in classpath then JSON marshalling is initialized as well for media type application/json
 * 
 * If user is unhappy with default marshallers, he can create and configure his own RequestJaxbMarshaller and set it via
 * httpSender.setMarshaller(customMarshaller, "application/xml");
 * httpSender.setMarshaller(customMarshaller, "text/xml");
 * 
 * @author martin.vanek
 *
 */
public class Marshallers {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static String marshall(HttlMarshaller marshaller, Object payload) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		marshaller.write(payload, baos, Charset.forName("utf-8"));
		baos.flush();
		return new String(baos.toByteArray());
	}

	private Map<String, HttlMarshaller> marshallers = new HashMap<String, HttlMarshaller>();

	/**
	 * Initiate Built in Marshallers
	 */
	public Marshallers() {

		HttlMarshaller marshaller = null;

		//First try SimpleXml
		try {
			Class.forName("org.simpleframework.xml.core.Persister");
			marshaller = new SimpleXmlMarshaller();
			//setMarshaller(marshaller, "text/xml");
			setMarshaller(marshaller, "application/xml");
		} catch (ClassNotFoundException cnfx) {
			//nothing
		}

		//Then try JAXB
		if (marshaller == null) {
			try {
				Class.forName("javax.xml.bind.JAXBContext"); //JAXB is not avaliable on Android
				marshaller = new JaxbMarshaller();
				//setMarshaller(marshaller, "text/xml");
				setMarshaller(marshaller, "application/xml");
			} catch (ClassNotFoundException cnfx) {
				//nothing
			}
		}

		if (marshaller == null) {
			logger.debug("No XML binding library found. XML body requests support is disabled");
		}

		marshaller = null;
		//JSON support

		//First try Jackson 2
		try {
			Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
			marshaller = new Jackson2Marshaller();
			setMarshaller(marshaller, "application/json");
		} catch (ClassNotFoundException cnfx) {
			//nothing
		}

		//Then try Jackson 1
		if (marshaller == null) {
			try {
				Class.forName("org.codehaus.jackson.map.ObjectMapper");
				marshaller = new Jackson1Marshaller();
				setMarshaller(marshaller, "application/json");
			} catch (ClassNotFoundException cnfx) {
				//nothing
			}
		}

		//Then try Gson
		if (marshaller == null) {
			try {
				Class.forName("com.google.gson.Gson");
				marshaller = new GsonMarshaller();
				setMarshaller(marshaller, "application/json");
			} catch (ClassNotFoundException cnfx) {
				//nothing
			}
		}

		if (marshaller == null) {
			logger.debug("No JSON binding library found. JSON body requests support is disabled");
		}
	}

	public HttlMarshaller getMarshaller(String mediaType) {
		return marshallers.get(mediaType);
	}

	/**
	 * Register request body marshaller with provided mediaType
	 */
	public void setMarshaller(HttlMarshaller marshaller, String mediaType) {
		if (Cutils.isBlank(mediaType)) {
			throw new IllegalArgumentException("media type is blank");
		}
		if (marshaller == null) {
			throw new IllegalArgumentException("marshaller is null");
		}
		logger.debug("Adding " + marshaller.getClass().getName() + " for " + mediaType);
		this.marshallers.put(mediaType, marshaller);
	}

	/**
	 * Shortcut to marshall request body
	
	public String marshall(Object body, String mediaType) throws IOException {
		RequestBodyMarshaller marshaller = getMarshaller(mediaType);
		if (marshaller == null) {
			throw new IllegalArgumentException("Marshaller found for Content-Type '" + mediaType + "'");
		}
		return marshaller.marshall(body);
	}
	 */

	@Override
	public String toString() {
		return "RequestMarshallerFactory [marshallers=" + marshallers + "]";
	}

}
