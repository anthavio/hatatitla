package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseInterceptor {

	public void onResponse(SenderResponse response);

}
