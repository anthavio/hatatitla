package net.anthavio.httl.api;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequest.Method;

/**
 * 
 * @author martin.vanek
 *
 */
public enum HttpMethod {
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
