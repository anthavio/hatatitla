package net.anthavio.httl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequest.Method;

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
	//TODO next version int timeout() default -1;

	public static enum HttpMethod {
		GET(HttlRequest.Method.GET), //
		POST(HttlRequest.Method.POST), //
		PUT(HttlRequest.Method.PUT), //
		DELETE(HttlRequest.Method.DELETE), //
		HEAD(HttlRequest.Method.HEAD), //
		OPTIONS(HttlRequest.Method.OPTIONS), //
		PATH_DERIVED(null);//determine method from path 

		private final HttlRequest.Method method;

		private HttpMethod(Method method) {
			this.method = method;
		}

		public HttlRequest.Method getMethod() {
			return method;
		}

	}
}
