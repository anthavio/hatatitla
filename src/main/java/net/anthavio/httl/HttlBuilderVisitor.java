package net.anthavio.httl;


/**
 * Allows modification of HttlRequest just before it gets executed
 * 
 * @author martin.vanek
 *
 */
public interface HttlBuilderVisitor {

	/**
	 * @param builder - enjoy!
	 */
	public void visit(HttlRequestBuilder<?> builder);
}
