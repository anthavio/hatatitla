package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author martin.vanek
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttlApi {

	/**
	 * @return url path prefix for all @HttlCall
	 */
	String value() default "";

	/**
	 * @return url path prefix for all @HttlCall
	 */
	String uri() default "";

	/**
	 * @return Configure commonly used VarSetter so it does not need to be decalared in every @HttlVar(setter=...)
	 */
	Class<? extends VarSetter>[] setters() default {};

}
