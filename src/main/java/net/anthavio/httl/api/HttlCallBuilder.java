package net.anthavio.httl.api;

/**
 * @author martin.vanek
 *
 * @param <R> return type
 */
public interface HttlCallBuilder<R> {

	public R execute();

}
