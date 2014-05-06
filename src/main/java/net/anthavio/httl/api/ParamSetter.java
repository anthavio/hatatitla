package net.anthavio.httl.api;

import net.anthavio.httl.SenderRequest;

/**
 * Optional 'setter' field of @Param annotation must implement this interface.
 * 
 * Example:
 * 
 * @Operation("GET /paging")
 * String paging(@Param(value = "page", setter = PageableSetter.class) Pageable pager);
 * 		
 * @author martin.vanek
 *
 */
public interface ParamSetter<T> {

	public void set(T value, String name, SenderRequest request);
}
