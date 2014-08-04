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
	 * Builde default Jackson ObjectMapper
	 */
	public static ObjectMapper build() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setDateFormat(new ISO8601DateFormat());
		return mapper;
	}
}
