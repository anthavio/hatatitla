package net.anthavio.httl.api;

/**
 * When Api @HttlCall method has overwhelming number of parameters to be declared, extend this interface 
 * and return it from @HttlCall method instead of response and let the magic happend
 * 
 * Bare in mind that reflection and dynamic proxy is employed which makes this awesome thing not as fast as light
 * 
 * Example:
 * 
 * @HttlCall("GET list.json")
 * public ListBlacklistBuilder list(@HttlVar(name = "forum", required = true) String forum);
 * 
 * public static interface ListBlacklistBuilder extends HttlCallBuilder<DisqusResponse<List<DisqusFilter>>> {
 * 
 * 	public ListBlacklistBuilder since(Date since);
 * 
 * 	// more builder methods...
 * 
 * 	public ListBlacklistBuilder order(Order order);
 * }
 * 
 * @author martin.vanek
 *
 * @param <R> return type
 */
public interface HttlCallBuilder<R> {

	/**
	 * Dynamicaly implemented
	 */
	public R execute();

}
