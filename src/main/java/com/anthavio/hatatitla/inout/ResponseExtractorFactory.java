package com.anthavio.hatatitla.inout;

import java.io.Serializable;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	public <T extends Serializable> ResponseBodyExtractor<T> getExtractor(Class<T> resultType);
}
