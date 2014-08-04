package net.anthavio.httl.api;

import java.lang.reflect.Method;

import net.anthavio.httl.HttlException;

/**
 * API build exception actually
 * 
 * @author martin.vanek
 *
 */
public class HttlApiException extends HttlException {

	private static final long serialVersionUID = 1L;

	protected HttlApiException(String message, Method method) {
		super(message + " " + method);
	}

	public HttlApiException(String message, Class<?> clazz) {
		super(message + " " + clazz);
	}

	public HttlApiException(String string, Exception x) {
		super(string);
	}

}
