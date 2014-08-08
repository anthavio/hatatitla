package net.anthavio.httl.marshall;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;

/**
 * 
 * @author martin.vanek
 *
 */
public class Jackson2Util {

	/**
	 * Builde default Jackson ObjectMapper
	 */
	public static ObjectMapper build() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setDateFormat(new ISO8601DateFormat());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}
}
