package com.anthavio.httl.inout;

import com.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Class<T> resultType);
}
