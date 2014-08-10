package net.anthavio.httl.marshall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.util.Cutils;

/**
 * Hatatilta's default Marshaller implementation using multiple Marshallers for different media types
 * 
 * Automatic discovery lookup is made
 * xml media type: JAXB, SimpleXml
 * json media type: Jackson2, Jackson1, Gson
 * 
 * @author martin.vanek
 *
 */
public class MediaTypeMarshaller implements HttlBodyMarshaller {

	public static String marshall(HttlBodyMarshaller marshaller, Object payload) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		marshaller.write(payload, baos, Charset.forName("utf-8"));
		baos.flush();
		return new String(baos.toByteArray());
	}

	private Map<String, HttlBodyMarshaller> marshallers = new HashMap<String, HttlBodyMarshaller>();

	@Override
	public HttlBodyMarshaller supports(HttlRequest request) {
		String mediaType = request.getMediaType();
		HttlBodyMarshaller marshaller = marshallers.get(mediaType);
		if (marshaller == null) {
			marshaller = OptinalLibs.findMarshaller(mediaType);
			if (marshaller != null) {
				marshallers.put(mediaType, marshaller);
			}
		}
		return marshaller.supports(request);
	}

	@Override
	public void write(Object payload, OutputStream stream, Charset charset) throws IOException {
		throw new IllegalStateException("Call support() to get right Marshaller");
	}

	public HttlBodyMarshaller getMarshaller(String mediaType) {
		return marshallers.get(mediaType);
	}

	/**
	 * Register Marshaller for mediaType
	 */
	public void setMarshaller(HttlBodyMarshaller marshaller, String mediaType) {
		if (Cutils.isBlank(mediaType)) {
			throw new IllegalArgumentException("media type is blank");
		}
		if (marshaller == null) {
			throw new IllegalArgumentException("marshaller is null");
		}
		//logger.debug("Adding " + marshaller.getClass().getName() + " for " + mediaType);
		this.marshallers.put(mediaType, marshaller);
	}

	@Override
	public String toString() {
		return "RequestMarshallerFactory [marshallers=" + marshallers + "]";
	}

}
