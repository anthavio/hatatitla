package net.anthavio.httl.marshall;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.HttlBodyUnmarshaller;
import net.anthavio.httl.HttlResponseException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.OptionalLibs;

/**
 * Hatatilta's default Unmarshaller implementation using multiple Unmarshaller for different media types
 * 
 * @author martin.vanek
 *
 */
public class MediaTypeUnmarshaller implements HttlBodyUnmarshaller {

	private Map<String, HttlBodyUnmarshaller> unmarshallers = new HashMap<String, HttlBodyUnmarshaller>();

	@Override
	public Object unmarshall(HttlResponse response, Type returnType) throws IOException {
		String mediaType = response.getMediaType();
		if (mediaType != null) {
			HttlBodyUnmarshaller unmarshaller = unmarshallers.get(mediaType);

			//Init default
			if (unmarshaller == null) {
				unmarshaller = OptionalLibs.findUnmarshaller(mediaType);
				if (unmarshaller != null) {
					unmarshallers.put(mediaType, unmarshaller);
				}
			}
			if (unmarshaller != null) {
				return unmarshaller.unmarshall(response, returnType);
			} else {
				throw new HttlResponseException(response, "No Unmarshaller for Response: " + response + " return type: "
						+ returnType);
			}
		} else {
			throw new HttlResponseException(response, "Response does not have Content-Type: " + response + " return type: "
					+ returnType);
		}

	}

	/**
	 * Add into first position
	 */
	public void setUnmarshaller(HttlBodyUnmarshaller unmarshaller, String mediaType) {
		if (Cutils.isBlank(mediaType)) {
			throw new IllegalArgumentException("Media type is blank");
		}
		if (unmarshaller == null) {
			throw new IllegalArgumentException("Unmarshaller is null");
		}
		unmarshallers.put(mediaType, unmarshaller);
	}

	/**
	 * Returned list is open for changes...
	 */
	public HttlBodyUnmarshaller getUnmarshallers(String mediaType) {
		return unmarshallers.get(mediaType);
	}

}
