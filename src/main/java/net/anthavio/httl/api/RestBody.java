package net.anthavio.httl.api;

import java.io.OutputStream;
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
public @interface RestBody {

	/**
	 * @return 'Content-Type' http request header value
	 */
	String value() default "";

	/**
	 * @return Optional HttlBodyWriter to serialize request body
	 */
	Class<? extends HttlBodyWriter> writer() default NullSurrogateHttlBodyWriter.class;

	/**
	 * Null surrogate class as annotation do not allow null...
	 */
	static class NullSurrogateHttlBodyWriter<T> implements HttlBodyWriter<T> {

		@Override
		public void write(T payload, OutputStream stream) {
		}

	}
}
