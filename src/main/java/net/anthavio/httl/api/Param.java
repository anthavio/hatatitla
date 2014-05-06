package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.anthavio.httl.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

	String value();

	Class<? extends ParamSetter> setter() default DefaultParamSetter.class;

	static class DefaultParamSetter<T> implements ParamSetter<T> {

		@Override
		public void set(T value, String name, SenderRequest request) {
			//nothing at all -  targets for params are calculated from annotations
		}
	}
}
