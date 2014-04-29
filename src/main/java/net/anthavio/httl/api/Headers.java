package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can contain placeholders for values supplied by method parameter
 * 
 * @author martin.vanek
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Headers {

	/**
	 * @return array of http request headers (possibly with placehoders)
	 */
	String[] value();
}
