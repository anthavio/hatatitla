package com.anthavio.client.http.inout;

import java.io.Serializable;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	public <T extends Serializable> ResponseBodyExtractor<T> getExtractor(Class<T> resultType);
}
