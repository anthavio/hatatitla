package net.anthavio.httl.api;

import net.anthavio.httl.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ParamSetter<T> {

	public void set(SenderRequest request, String name, T value);
}
