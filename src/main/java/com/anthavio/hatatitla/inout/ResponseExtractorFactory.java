package com.anthavio.hatatitla.inout;

import com.anthavio.hatatitla.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Class<T> resultType);
}
