package net.anthavio.httl;

import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;

/**
 * Request builder interceptor allows modification of request just berore it is executed
 * 
 * @author martin.vanek
 *
 */
public interface HttlBuilderInterceptor {

	public void onBuild(HttlRequestBuilder<?> builder);
}
