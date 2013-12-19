package net.anthavio.httl;

import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

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
