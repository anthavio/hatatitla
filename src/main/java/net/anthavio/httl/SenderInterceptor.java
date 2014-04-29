package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 *
 */
public interface SenderInterceptor {

	public void onClose(HttpSender sender);
}
