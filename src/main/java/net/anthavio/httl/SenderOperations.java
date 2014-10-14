package net.anthavio.httl;


/**
 * 
 * @author martin.vanek
 *
 */
public interface SenderOperations {

	public HttlResponse execute(HttlRequest request) throws HttlException;

	//public void execute(SenderRequest request, ResponseHandler handler) throws SenderException;

}
