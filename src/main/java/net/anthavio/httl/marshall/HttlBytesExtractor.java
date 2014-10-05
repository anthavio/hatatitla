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
public class HttlBytesExtractor implements HttlResponseExtractor<byte[]> {

	private int httpMin;
	private int httpMax;

	public HttlBytesExtractor(int httpMin, int httpMax) {
		this.httpMin = httpMin;
		this.httpMax = httpMax;
	}

	@Override
	public byte[] extract(HttlResponse response) throws IOException {
		if (response.getHttpStatusCode() > httpMax || response.getHttpStatusCode() < httpMin) {
			throw new HttlStatusException(response);
		} else {
			return HttlUtil.readAsBytes(response);
		}
	}

}
