package com.anthavio.hatatitla.inout;

import java.io.Serializable;

import com.anthavio.hatatitla.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	public <T extends Serializable> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Class<T> resultType);
}
