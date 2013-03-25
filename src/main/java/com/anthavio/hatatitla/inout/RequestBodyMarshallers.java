package com.anthavio.hatatitla.inout;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.hatatitla.Cutils;

/**
 * Storage for HttpSender's RequestBodyMarshallers. JAXB marshaller is allways installed fro mime types text/xml and application/xml
 * When Jackson is found in classpath then JSON marshalling is initialized as well for mime type application/json
 * 
 * If user is unhappy with default marshallers, he can create and configure his own RequestJaxbMarshaller and set it via
 * httpSender.setMarshaller("application/xml", customJaxbMarshaller);
 * httpSender.setMarshaller("text/xml", customJaxbMarshaller);
 * 
 * @author martin.vanek
 *
 */
public class RequestBodyMarshallers {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map<String, RequestBodyMarshaller> marshallers = new HashMap<String, RequestBodyMarshaller>();

	/**
	 * Initiate Built in Marshallers
	 */
	public RequestBodyMarshallers() {

		JaxbRequestMarshaller jaxbMarshaller = new JaxbRequestMarshaller();
		marshallers.put("text/xml", jaxbMarshaller);
		marshallers.put("application/xml", jaxbMarshaller);
		logger.debug("Adding RequestJaxbMarshaller for XML requests");

		//Jackson support is optional
		try {
			Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
			marshallers.put("application/json", new Jackson2RequestMarshaller());
			logger.debug("Adding RequestJackson2Marshaller for JSON requests");
		} catch (ClassNotFoundException cnf) {
			try {
				Class.forName("org.codehaus.jackson.map.ObjectMapper");
				marshallers.put("application/json", new Jackson1RequestMarshaller());
				logger.debug("Adding RequestJackson1Marshaller for JSON requests");
			} catch (ClassNotFoundException cnf2) {
				logger.debug("Jackson classes not found. Built in JSON requests support is off");
			}
		}
	}

	public RequestBodyMarshaller getMarshaller(String mimeType) {
		return marshallers.get(mimeType);
	}

	public void setMarshaller(String mimeType, RequestBodyMarshaller marshaller) {
		if (Cutils.isBlank(mimeType)) {
			throw new IllegalArgumentException("mime type is blank");
		}
		if (marshaller == null) {
			throw new IllegalArgumentException("marshaller is null");
		}
		this.marshallers.put(mimeType, marshaller);
	}

	/**
	 * Shortcut to marshall request body
	
	public String marshall(Object body, String mimeType) throws IOException {
		RequestBodyMarshaller marshaller = getMarshaller(mimeType);
		if (marshaller == null) {
			throw new IllegalArgumentException("Marshaller found for Content-Type '" + mimeType + "'");
		}
		return marshaller.marshall(body);
	}
	 */

	@Override
	public String toString() {
		return "RequestMarshallerFactory [marshallers=" + marshallers + "]";
	}

}
