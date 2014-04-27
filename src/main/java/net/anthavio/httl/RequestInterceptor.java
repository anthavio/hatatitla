package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 *
 */
public interface RequestInterceptor {

	public void onRequest(SenderRequest request);
}
