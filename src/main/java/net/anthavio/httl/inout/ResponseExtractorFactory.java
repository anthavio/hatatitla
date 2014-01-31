package net.anthavio.httl.inout;

import java.lang.reflect.ParameterizedType;

import net.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractorFactory {

	/**
	 * @return ResponseBodyExtractor for given SenderResponse and Class<T> resultType
	 */
	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, Class<T> resultType);

	public <T> ResponseBodyExtractor<T> getExtractor(SenderResponse response, ParameterizedType resultType);
}
