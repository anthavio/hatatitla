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
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestCall {

	HttpMethod method() default HttpMethod.PATH_DERIVED;

	/**
	 * @return "GET /something"
	 */
	String value();

	/**
	 * @return
	 */
	int timeout() default -1;
}
