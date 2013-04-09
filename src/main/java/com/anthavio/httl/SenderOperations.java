package com.anthavio.httl;


/**
 * 
 * @author martin.vanek
 *
 */
public interface SenderOperations {

	public SenderResponse execute(SenderRequest request) throws SenderException;

	//public void execute(SenderRequest request, ResponseHandler handler) throws SenderException;

}
