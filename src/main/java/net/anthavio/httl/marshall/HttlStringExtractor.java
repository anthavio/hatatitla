package net.anthavio.httl.marshall;

import java.io.IOException;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlStatusException;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlStringExtractor implements HttlResponseExtractor<String> {

	public static HttlStringExtractor DEFAULT = new HttlStringExtractor(200, 300);

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
			return HttpHeaderUtil.readAsString(response);
		}
	}

}
