package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 
 * @author martin.vanek
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

	String value();
	/*
		Class<? extends ParamSetter> set() default ToStringSetter.class;

		static class ToStringSetter<T> implements ParamSetter<T> {

			@Override
			public void set(SenderRequest request, String name, T value) {
				// TODO Auto-generated method stub

			}
		}
	*/
}
