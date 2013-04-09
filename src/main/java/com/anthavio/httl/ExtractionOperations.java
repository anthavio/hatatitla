package com.anthavio.httl;

import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ExtractionOperations {

	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, ResponseBodyExtractor<T> extractor)
			throws SenderException;

	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType) throws SenderException;
}
