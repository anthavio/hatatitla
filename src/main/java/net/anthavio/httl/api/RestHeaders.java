package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Values can be {placeholders} for values supplied by method parameters
 * 
 * @author martin.vanek
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RestHeaders {

	/**
	 * @return array of http request headers (possibly with placehoders)
	 */
	String[] value();
}
