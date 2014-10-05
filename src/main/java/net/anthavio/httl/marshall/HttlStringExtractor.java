package net.anthavio.httl.marshall;

import java.io.IOException;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlStatusException;
import net.anthavio.httl.util.HttlUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlStringExtractor implements HttlResponseExtractor<String> {

	/**
	 * throws HttlStatusException when response http status is not in <200,299> range
	 */
	public static HttlStringExtractor STANDARD = new HttlStringExtractor(200, 299);

	/**
	 * Accepts any response http status
	 */
	public static HttlStringExtractor RELAXED = new HttlStringExtractor(0, 1000);

	private int httpMin;
	private int httpMax;

	public HttlStringExtractor(int httpMin, int httpMax) {
		this.httpMin = httpMin;
		this.httpMax = httpMax;
	}

	@Override
	public String extract(HttlResponse response) throws IOException {
		if (response.getHttpStatusCode() > httpMax || response.getHttpStatusCode() < httpMin) {
			throw new HttlStatusException(response);
		} else {
			return HttlUtil.readAsString(response);
		}
	}

}
