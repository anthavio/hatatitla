package com.anthavio.httl;

import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.inout.ResponseHandler;

/**
 * 
 * @author martin.vanek
 *
 */
public interface SenderOperations {

	public SenderResponse execute(SenderRequest request) throws SenderException;

	public void execute(SenderRequest request, ResponseHandler handler) throws SenderException;

	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, ResponseBodyExtractor<T> extractor)
			throws SenderException;

	public <T> ExtractedBodyResponse<T> extract(SenderRequest request, Class<T> resultType) throws SenderException;

}
