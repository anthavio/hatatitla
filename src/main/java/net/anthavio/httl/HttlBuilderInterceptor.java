package net.anthavio.httl;

import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;

/**
 * Allows modification of HttlRequest just before it gets executed
 * 
 * @author martin.vanek
 *
 */
public interface HttlBuilderInterceptor {

	public void onBuild(HttlRequestBuilder<?> builder);
}
