package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Values can be {placeholders} for values supplied by method parameters
 * 
 * Examples:
 * 
 * @HttlHeaders("User-Agent: Hatatitla")
 * 
 * @HttlHeaders({"User-Agent: {user_agent}", "Accept: application/json"})
 * 
 * @author martin.vanek
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface HttlHeaders {

	/**
	 * @return array of http request headers (possibly with placehoders)
	 */
	String[] value();
}
