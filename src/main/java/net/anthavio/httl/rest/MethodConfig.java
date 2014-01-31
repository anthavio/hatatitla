package net.anthavio.httl.rest;

import java.lang.reflect.ParameterizedType;

import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.rest.ArgConfig.ArgType;
import net.anthavio.httl.util.GenericType;

/**
 * Method configuration template
 * 
 * @author martin.vanek
 * 
 */
public class MethodConfig<R> {

	public static enum Access {
		PUBLIC_KEY, SECRET_KEY;
	}

	public static enum ClientAuth {
		NONE, OPTIONAL, REQUIRED;
	}

	public static enum MimeType {
		JSON, JSONP, XML;
	}

	public static class ResponseConfig<R> {

		private final Class<R> clazz;

		private final boolean multi;

		private final boolean cursor;

		private final MimeType[] formats;

		private final ParameterizedType type;

		public ResponseConfig(ParameterizedType type, MimeType[] formats, boolean multi, boolean cursor) {
			this.clazz = null;
			this.type = type;
			this.formats = formats;
			this.multi = multi;
			this.cursor = cursor;

		}

		public ResponseConfig(Class<R> clazz, MimeType[] formats, boolean multi, boolean cursor) {
			this.clazz = clazz;
			this.type = null;
			this.formats = formats;
			this.multi = multi;
			this.cursor = cursor;
		}

		public Class<R> getClazz() {
			return this.clazz;
		}

		public ParameterizedType getType() {
			return this.type;
		}

		public MimeType[] getFormats() {
			return this.formats;
		}

		public boolean getMulti() {
			return this.multi;
		}

		public boolean getCursor() {
			return this.cursor;
		}

	}

	public SenderRequest buildRequest() {
		//XXX http/https
		return new SenderRequest(method, urlPath);
	}

	private final boolean https;

	private final SenderRequest.Method method;

	private final String urlPath;

	//private final Access[] access;

	private final ClientAuth authentication;

	private final ArgConfig[] arguments;

	private final ResponseConfig<R> response;

	public MethodConfig(boolean https, SenderRequest.Method method, String urlPath, ClientAuth authentication,
			ResponseConfig<R> response, ArgConfig... arguments) {
		this.https = https;
		this.method = method;
		this.urlPath = urlPath;
		//this.access = accesibility;
		this.authentication = authentication;
		this.arguments = arguments;
		this.response = response;
	}

	public boolean getHttps() {
		return https;
	}

	public SenderRequest.Method getMethod() {
		return this.method;
	}

	public String getUrlPath() {
		return this.urlPath;
	}

	public ArgConfig[] getArguments() {
		return this.arguments;
	}

	/*
		public Access[] getAccess() {
			return this.access;
		}
	*/
	public ClientAuth getAuthentication() {
		return this.authentication;
	}

	public Class<R> getResponseClass() {
		return response.getClazz();
	}

	public ParameterizedType getResponseType() {
		return response.getType();
	}

	/**
	 * POST
	 */
	public static <R> MethodConfig<R> POST(String urlPath, ClientAuth authentication, Class<R> responseClass,
			ArgConfig... arguments) {
		return Method(false, SenderRequest.Method.POST, urlPath, authentication, responseClass, arguments);
	}

	/**
	 * POST anonymous
	 */
	public static <R> MethodConfig<R> POST(String urlPath, Class<R> responseClass, ArgConfig... arguments) {
		return POST(urlPath, ClientAuth.NONE, responseClass, arguments);
	}

	/**
	 * PUT - GenericType
	 */
	public static <R> MethodConfig<R> POST(String urlPath, GenericType<R> responseType, ArgConfig... arguments) {
		ResponseConfig<R> response = new ResponseConfig<R>(responseType.getParameterizedType(), null, true, true);
		return new MethodConfig<R>(false, SenderRequest.Method.POST, urlPath, ClientAuth.NONE, response, arguments);
	}

	/**
	 * GET
	 */
	public static <R> MethodConfig<R> GET(String urlPath, ClientAuth authentication, Class<R> responseClass,
			ArgConfig... arguments) {
		return Method(false, SenderRequest.Method.GET, urlPath, authentication, responseClass, arguments);
	}

	/**
	 * GET anonymous
	 */
	public static <R> MethodConfig<R> GET(String urlPath, Class<R> responseClass, ArgConfig... arguments) {
		return Method(false, SenderRequest.Method.GET, urlPath, ClientAuth.NONE, responseClass, arguments);
	}

	/**
	 * GET - GenericType
	 */
	public static <R> MethodConfig<R> GET(String urlPath, GenericType<R> responseType, ArgConfig... arguments) {
		ResponseConfig<R> response = new ResponseConfig<R>(responseType.getParameterizedType(), null, true, true);
		return new MethodConfig<R>(false, SenderRequest.Method.GET, urlPath, ClientAuth.NONE, response, arguments);
	}

	/**
	 * PUT
	 */
	public static <R> MethodConfig<R> PUT(String urlPath, ClientAuth authentication, Class<R> responseClass,
			ArgConfig... arguments) {
		return Method(false, SenderRequest.Method.PUT, urlPath, authentication, responseClass, arguments);
	}

	/**
	 * PUT anonymous
	 */
	public static <R> MethodConfig<R> PUT(String urlPath, Class<R> responseClass, ArgConfig... arguments) {
		return PUT(urlPath, ClientAuth.NONE, responseClass, arguments);
	}

	/**
	 * PUT - GenericType
	 */
	public static <R> MethodConfig<R> PUT(String urlPath, GenericType<R> responseType, ArgConfig... arguments) {
		ResponseConfig<R> response = new ResponseConfig<R>(responseType.getParameterizedType(), null, true, true);
		return new MethodConfig<R>(false, SenderRequest.Method.PUT, urlPath, ClientAuth.NONE, response, arguments);
	}

	/**
	 * DELETE
	 */
	public static <R> MethodConfig<R> DELETE(String urlPath, ClientAuth authentication, Class<R> responseClass,
			ArgConfig... arguments) {
		return Method(false, SenderRequest.Method.DELETE, urlPath, authentication, responseClass, arguments);
	}

	/**
	 * DELETE anonymous
	 */
	public static <R> MethodConfig<R> DELETE(String urlPath, Class<R> responseClass, ArgConfig... arguments) {
		return DELETE(urlPath, ClientAuth.NONE, responseClass, arguments);
	}

	public static <R> MethodConfig<R> Method(boolean https, SenderRequest.Method method, String urlPath,
			ClientAuth authentication, Class<R> responseClass, ArgConfig... arguments) {
		ResponseConfig<R> response = new ResponseConfig<R>(responseClass, null, true, true);
		//add keys and authentication parameters
		/*
		int alen = arguments.length;
		ArgConfig[] argsPlus = new ArgConfig[alen + 4];
		System.arraycopy(arguments, 0, argsPlus, 0, alen);
		argsPlus[alen] = ArgConfig.API_KEY;
		argsPlus[alen + 1] = ArgConfig.SECRET_KEY;
		argsPlus[alen + 2] = ArgConfig.ACCESS_TOKEN;
		argsPlus[alen + 3] = ArgConfig.REMOTE_AUTH;
		*/
		return new MethodConfig<R>(https, method, urlPath, authentication, response, arguments);
	}

	/**
	 * Optional single value argument
	 */
	public static ArgConfig Opt(String name, ArgType<?, ?> type) {
		return Arg(name, type, false, false);
	}

	/**
	 * Optional multi value argument
	 */
	public static ArgConfig Opt(String name, ArgType<?, ?> type, boolean multi) {
		return Arg(name, type, false, multi);
	}

	/**
	 * Required single value argument
	 */
	public static ArgConfig Req(String name, ArgType<?, ?> type) {
		return Arg(name, type, true, false);
	}

	/**
	 * Required multi value argument
	 */
	public static ArgConfig Req(String name, ArgType<?, ?> type, boolean multi) {
		return Arg(name, type, true, multi);
	}

	public static ArgConfig Arg(String name, ArgType<?, ?> type, boolean required, boolean multi) {
		return new ArgConfig(name, type, required, multi);
	}

}
