package net.anthavio.httl.api;

import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderRequest.Method;

/**
 * 
 * @author martin.vanek
 *
 */
public enum HttpMethod {
	GET(SenderRequest.Method.GET), //
	POST(SenderRequest.Method.POST), //
	PUT(SenderRequest.Method.PUT), //
	DELETE(SenderRequest.Method.DELETE), //
	HEAD(SenderRequest.Method.HEAD), //
	OPTIONS(SenderRequest.Method.OPTIONS), //
	PATH(null);//determine method from path 

	private final SenderRequest.Method method;

	private HttpMethod(Method method) {
		this.method = method;
	}

	public SenderRequest.Method getMethod() {
		return method;
	}

}
