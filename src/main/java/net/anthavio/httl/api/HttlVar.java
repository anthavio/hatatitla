package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.anthavio.httl.HttlRequestBuilder;

/**
 * 
 * @author martin.vanek
 *
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface HttlVar {

	public static final String NULL_STRING_SURROGATE = "##NULL##";

	/**
	 * Name of the rest paramater. Lousy named for 'name' but allows simple annotation syntax
	 */
	String value() default NULL_STRING_SURROGATE;

	/**
	 * Name of the rest paramater. Use rather than misleading value()
	 */
	String name() default NULL_STRING_SURROGATE;

	/**
	 * Mark parameter as required - prevents null values
	 */
	boolean required() default false;

	/**
	 * Used instead of null value (when parameter is not required())
	 */
	String defval() default NULL_STRING_SURROGATE;

	/**
	 * Optionaly use custom setter for complex parameter or parameter that needs conversion
	 */
	Class<? extends VarSetter> setter() default NoopParamSetter.class;

	static class NoopParamSetter<T> implements VarSetter<T> {

		@Override
		public void set(T value, String name, HttlRequestBuilder<?> builder) {
			//nothing at all -  targets for params are calculated from annotations
		}
	}

}
