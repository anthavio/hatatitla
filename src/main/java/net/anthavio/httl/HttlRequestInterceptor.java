package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlRequestInterceptor {

	/**
	 * Callback method executed just before sending
	 */
	public void onSend(HttlRequest request);

}
