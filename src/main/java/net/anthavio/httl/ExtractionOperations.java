package net.anthavio.httl;

import net.anthavio.httl.ResponseExtractor.ExtractedResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ExtractionOperations {

	public <T> ExtractedResponse<T> extract(HttlRequest request, ResponseExtractor<T> extractor)
			throws HttlException;

	public <T> ExtractedResponse<T> extract(HttlRequest request, Class<T> resultType) throws HttlException;
}
