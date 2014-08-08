package net.anthavio.httl;

import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;

/**
 * Allows modification of HttlRequest just before it gets executed
 * 
 * @author martin.vanek
 *
 */
public interface HttlBuilderVisitor {

	public void visit(HttlRequestBuilder<?> builder);
}
