package net.anthavio.httl.marshall;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.util.ISO8601DateFormat;

/**
 * 
 * @author martin.vanek
 *
 */
public class Jackson1Util {

	/**
	 * Build default Jackson ObjectMapper
	 */
	public static ObjectMapper build() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setDateFormat(new ISO8601DateFormat());
		mapper.configure(org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}
}
