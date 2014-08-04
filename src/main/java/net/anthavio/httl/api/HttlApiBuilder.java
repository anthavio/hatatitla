package net.anthavio.httl.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.anthavio.httl.HttlBuilderInterceptor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionInterceptor;
import net.anthavio.httl.HttlMarshaller;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlSender.Parameters;
import net.anthavio.httl.api.RestBody.NoopRequestMarshaller;
import net.anthavio.httl.api.RestVar.NoopParamSetter;
import net.anthavio.httl.api.VarSetter.ComplexMetaVarSetter;
import net.anthavio.httl.api.VarSetter.FieldApiVarMeta;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlApiBuilder {

	public static HttlApiBuilder with(HttlSender sender) {
		return new HttlApiBuilder(sender);
	}

	private final HttlSender sender;

	private HttpHeaders headers;

	private Parameters params;

	public HttlApiBuilder(HttlSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("Null sender");
		}
		this.sender = sender;
		params = new Parameters();
	}

	/**
	 * Add this header into every request
	 */
	public HttlApiBuilder addHeader(String name, String value) {
		if (headers == null) {
			headers = new HttpHeaders();
		}
		headers.add(name, value);
		return this;
	}

	/**
	 * Add this parameter into every request
	 */
	public HttlApiBuilder addParam(String name, String value) {
		if (params == null) {
			params = new Parameters();
		}
		params.add(name, value);
		return this;
	}

	public <T> T build(Class<T> apiInterface) throws HttlApiException {
		return build(apiInterface, sender, headers, params);
	}

	public static <T> T build(Class<T> apiInterface, HttlSender sender) throws HttlApiException {
		return build(apiInterface, sender, null, null);
	}

	public static <T> T build(Class<T> apiInterface, HttlSender sender, HttpHeaders headers, Parameters params) {
		RestApi annotation = apiInterface.getAnnotation(RestApi.class);
		String urlPathPrefix = (annotation != null) ? annotation.value() : "";
		Map<Method, ApiMethodMeta> methods = doApiMethods(apiInterface, urlPathPrefix);
		InvocationHandler handler = new HttlApiHandler<T>(apiInterface, sender, headers, params, methods);
		return (T) Proxy.newProxyInstance(apiInterface.getClassLoader(), new Class<?>[] { apiInterface }, handler);
	}

	private static Map<Method, ApiMethodMeta> doApiMethods(Class<?> apiInterface, String urlPathPrefix) {
		Map<Method, ApiMethodMeta> metaMap = new HashMap<Method, ApiMethodMeta>();
		for (Method method : apiInterface.getDeclaredMethods()) {
			RestCall operation = method.getAnnotation(RestCall.class);
			if (operation == null) {
				String mname = method.getName();
				if ("equals".equals(mname) || "hashCode".equals(mname)) {
					continue;
				}
				//Maybe just ignore those ???
				throw new HttlApiException("@RestCall not found ", method);
			}

			HttpMethod http = operation.method();
			String urlPath = operation.value();
			if (http == HttpMethod.PATH_DERIVED) {
				String[] segments = urlPath.split(" ");
				if (segments.length != 2) {
					throw new HttlApiException("Invalid @RestCall format: " + urlPath, method);
				}
				//maybe nicer exceptions here
				http = HttpMethod.valueOf(segments[0]);
				urlPath = segments[1];
			}
			urlPath = HttpHeaderUtil.joinUrlParts(urlPathPrefix, urlPath);

			//@RestVar annotations
			ApiVarMeta[] params = doApiParameters(method);
			Map<String, ApiVarMeta> paramMap = new HashMap<String, HttlApiBuilder.ApiVarMeta>();
			for (ApiVarMeta param : params) {
				paramMap.put(param.name, param);
			}

			//search for {placehoders} in url path
			int lastOpn = -1;
			while ((lastOpn = urlPath.indexOf('{', lastOpn + 1)) != -1) {
				int lastCls = urlPath.indexOf('}', lastOpn + 1);
				if (lastCls == -1) {
					throw new HttlApiException("Unpaired bracket in path " + urlPath, method);
				}

				String pathParam = urlPath.substring(lastOpn + 1, lastCls);
				ApiVarMeta param = paramMap.get(pathParam);
				if (param == null) {
					throw new HttlApiException("Parameter '" + pathParam + "' not found for path placeholder", method);
				} else {
					param.setTarget(VarTarget.PATH);
				}
			}
			//XXX maybe some prevention against duplicate path/header placeholders

			//@Header annotations
			ApiHeaderMeta[] headers = doApiHeaders(apiInterface, method);
			Map<String, ApiHeaderMeta> headerMap = new HashMap<String, HttlApiBuilder.ApiHeaderMeta>();
			for (ApiHeaderMeta header : headers) {
				if (header.templated) {
					ApiVarMeta param = paramMap.get(header.value); //Templated @Header needs @Param with value
					if (param != null) {
						param.setTargetHeader(header.name);
					} else {
						throw new HttlApiException("Templated Header '" + header.value + "' not found as parameter", method);
					}
				}
				headerMap.put(header.name, header);
			}

			//Some sanity checks...

			ApiVarMeta body = paramMap.get(BODY);

			if (body != null) {
				if (http.getMethod().isBodyAllowed() == false) {
					throw new HttlApiException("Body in not allowed on HTTP " + http.getMethod(), method);
				}

				if (body.variable == null && headerMap.get(HttlConstants.Content_Type) == null) {
					throw new HttlApiException("Content-Type not specified. Neither as a @Header nor as @Body parameter", method);
				}

				//maybe set priority instead of exception... maybe
				if (body.variable != null && headerMap.get(HttlConstants.Content_Type) != null) {
					throw new HttlApiException("Content-Type specified twice. As a @Header and as @Body parameter", method);
				}

			} else { // @Body param does not exist
				if (paramMap.get("#" + HttlMarshaller.class.getSimpleName()) != null) {
					throw new HttlApiException("@Body parameter is required when using RequestBodyMarshaller", method);
				}
			}
			// parameters not found as path or header placeholder are left as query parameters

			//return type is Builder subinterface
			BuilderMeta builderMeta = null;
			if (method.getReturnType().isInterface() && method.getReturnType().getInterfaces().length > 0
					&& method.getReturnType().getInterfaces()[0] == HttlCallBuilder.class) {
				//XXX this will fail if user will build some hierarchy of interfaces
				Class<? extends HttlCallBuilder> builderInterface = (Class<? extends HttlCallBuilder>) method.getReturnType();
				ParameterizedType parametrized = (ParameterizedType) builderInterface.getGenericInterfaces()[0];
				Type returnType = parametrized.getActualTypeArguments()[0];
				Map<Method, BuilderMethodMeta> builderMethods = doBuilderMethods(builderInterface);
				builderMeta = new BuilderMeta(builderInterface, returnType, builderMethods);
			}

			ApiMethodMeta methodMeta = new ApiMethodMeta(method, http, urlPath, params, headers, builderMeta);
			metaMap.put(method, methodMeta);
		}
		return metaMap;
	}

	private static Map<Method, BuilderMethodMeta> doBuilderMethods(Class<? extends HttlCallBuilder> builderInterface) {
		Map<Method, BuilderMethodMeta> metaMap = new HashMap<Method, BuilderMethodMeta>();
		Method[] methods = builderInterface.getDeclaredMethods();
		for (Method method : methods) {

			if (method.getName().equals("execute") && method.getParameterTypes().length == 0) {
				continue;
			}

			if (method.getReturnType() != builderInterface) {
				throw new HttlApiException("Invalid return: " + method.getReturnType() + " expected: " + builderInterface,
						method);
			}
			//We may support more parameters in future if it makes sense
			if (method.getParameterTypes().length != 1) {
				throw new HttlApiException("Single parameter is expected", method);
			}

			String paramName = method.getName();
			boolean replace = false;
			if (paramName.startsWith("set")) {
				replace = true;
				paramName = toPropName(paramName);
			} else if (paramName.startsWith("add")) {
				replace = false;
				paramName = toPropName(paramName);
			}

			ApiVarMeta varMeta = doParameter(0, method, paramName);
			BuilderMethodMeta methodMeta = new BuilderMethodMeta(method, varMeta, replace);
			metaMap.put(method, methodMeta);
		}
		return metaMap;
	}

	private final static String BODY = "#body";

	private static ApiHeaderMeta[] doApiHeaders(Class<?> clazz, Method method) {
		List<ApiHeaderMeta> headerList = new ArrayList<HttlApiBuilder.ApiHeaderMeta>();
		Map<String, Integer> headerMap = new HashMap<String, Integer>();

		//Process Type declared (global) annotations first
		RestHeaders cheaders = clazz.getAnnotation(RestHeaders.class);
		if (cheaders != null && cheaders.value().length != 0) {
			for (String header : cheaders.value()) {
				if (header.indexOf('{') != -1 && header.indexOf('}') != -1) {
					throw new HttlApiException("Static headers cannot be templated: " + header, clazz);
				}
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new HttlApiException("Static header syntax error: " + header, clazz);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				ApiHeaderMeta metaHeader = new ApiHeaderMeta(name, value, false);
				headerList.add(metaHeader);
				headerMap.put(name, headerList.size() - 1);
			}
		}

		//Process Method declared (local) annotations now
		RestHeaders mheaders = method.getAnnotation(RestHeaders.class);
		if (mheaders != null && mheaders.value().length != 0) {
			for (String header : mheaders.value()) {
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new HttlApiException("Method header syntax error: " + header, method);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				boolean templated = false;

				int idxOpn = value.indexOf('{');
				int idxCls = value.indexOf('}', idxOpn + 1);
				if (idxOpn != -1) {
					if (idxCls != -1) {
						value = value.substring(idxOpn + 1, idxCls);
						templated = true;
					} else {
						throw new HttlApiException("Unpaired brackets in method header: " + header, method);
					}
				}
				ApiHeaderMeta metaHeader = new ApiHeaderMeta(name, value, templated);
				Integer index = headerMap.get(name);
				if (index != null) {
					headerList.set(index, metaHeader); //replace shared if exists
				} else {
					headerList.add(metaHeader);
				}
			}
		}
		return headerList.toArray(new ApiHeaderMeta[headerList.size()]);
	}

	private static ApiVarMeta[] doApiParameters(Method method) {
		Map<String, ApiVarMeta> map = new HashMap<String, ApiVarMeta>();//only for duplicate name checks
		Class<?>[] types = method.getParameterTypes();
		List<ApiVarMeta> metaList = new ArrayList<ApiVarMeta>();
		for (int i = 0; i < types.length; i++) {
			Class<?> type = types[i];
			String name;
			ApiVarMeta meta;
			// interceptors/marshallers/extractors first - they need no annotation with name
			if (HttlBuilderInterceptor.class.isAssignableFrom(type)) {
				//TODO Multiple BuilderInterceptor parameters is ok - just use different name
				name = "#" + HttlBuilderInterceptor.class.getSimpleName() + "-" + i;
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.BLD_INTERCEPTOR);
				metaList.add(meta);

			} else if (HttlExecutionInterceptor.class.isAssignableFrom(type)) {
				//TODO Multiple ExecutionInterceptor parameters is ok - just use different name
				name = "#" + HttlExecutionInterceptor.class.getSimpleName() + "-" + i;
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.EXE_INTERCEPTOR);
				metaList.add(meta);

				/*
				} else if (RequestInterceptor.class.isAssignableFrom(type)) {
				name = "#" + RequestInterceptor.class.getSimpleName();
				meta = new ApiParamMeta(i, name, true, type, null, null, ParamTarget.REQ_INTERCEPTOR);
				metaList.add(meta);

				} else if (ResponseInterceptor.class.isAssignableFrom(type)) {
				name = "#" + ResponseInterceptor.class.getSimpleName();
				meta = new ApiParamMeta(i, name, true, type, null, null, ParamTarget.RES_INTERCEPTOR);
				metaList.add(meta);
				*/
			} else if (HttlMarshaller.class.isAssignableFrom(type)) {
				name = "#" + HttlMarshaller.class.getSimpleName();
				if (map.containsKey(name)) {
					throw new HttlApiException("Multiple RequestMarshaller parameters found", method);
				}
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.REQ_MARSHALLER);
				metaList.add(meta);

			} else if (HttlResponseExtractor.class.isAssignableFrom(type)) {
				//When parameter is declared as HttlResponseExtractor<Date>, generic information is erased...
				if (type.getGenericInterfaces().length != 0) { //real implementation like DateExtractor
					Type typeArg = ((ParameterizedType) type.getGenericInterfaces()[0]).getActualTypeArguments()[0];
					if (typeArg != method.getReturnType()) {
						throw new HttlApiException("Incompatible Extractor type: " + typeArg + " method returns : "
								+ method.getReturnType(), method);
					}
				}
				// versus method.getReturnType();
				// unfortunately type check of method return type and ResponseExtractor generic parameter
				// cannot be performed here...because of generic type erasure
				name = "#" + HttlResponseExtractor.class.getSimpleName();
				if (map.containsKey(name)) {
					throw new HttlApiException("Multiple ResponseExtractor parameters found", method);
				}
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.RES_EXTRACTOR);
				metaList.add(meta);

			} else {
				// normal parameters here...
				meta = doApiParameter(i, method);
				if (map.containsKey(meta.name)) {
					throw new HttlApiException("Duplicate parameter named '" + meta.name + "' found", method);
				}
				map.put(meta.name, meta);
				metaList.add(meta);
			}

		}
		return metaList.toArray(new ApiVarMeta[metaList.size()]);
	}

	private static ApiVarMeta doApiParameter(int index, Method method) {
		Annotation[][] mannotations = method.getParameterAnnotations();
		if (mannotations.length == 0) {
			throw new HttlApiException("Cannot determine parameter's name on position " + (index + 1)
					+ ". No annotation present", method);
		}
		return doParameter(index, method, null);

	}

	private static ApiVarMeta doParameter(int index, Method method, String defaultName) {

		String name = defaultName;
		boolean required = false;
		String nullval = null;
		VarSetter<Object> setter = null;
		HttlMarshaller marshaller = null;
		String variable = null;
		VarTarget target = VarTarget.QUERY;

		Annotation[] pannotations = method.getParameterAnnotations()[index];
		for (Annotation annotation : pannotations) {
			if (annotation instanceof RestVar) {
				RestVar var = (RestVar) annotation;
				name = getRestVarName(var);
				required = var.required();
				if (!var.defval().equals(RestVar.NULL_STRING_SURROGATE)) {
					nullval = var.defval();
				}
				if (var.setter() != NoopParamSetter.class) {
					try {
						setter = var.setter().newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Cannot create " + var.setter().getName() + " instance ", x);
					}
				}

			} else if (annotation instanceof RestBody) {
				RestBody body = (RestBody) annotation;
				name = BODY; //artificial
				target = VarTarget.BODY;
				if (!body.value().isEmpty()) {
					variable = body.value(); //Content-Type header
				}
				if (body.marshaller() != NoopRequestMarshaller.class) {
					try {
						marshaller = body.marshaller().newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Cannot create " + body.marshaller().getName() + " instance ", x);
					}
				}
			} else if (annotation.getClass().getName().equals("javax.inject.Named")) {
				try {
					Method mvalue = annotation.getClass().getMethod("value");
					name = (String) mvalue.invoke(annotation);
				} catch (Exception x) {
					throw new IllegalStateException("Reflective invocation javax.inject.Named.value()", x);
				}
			} else { // Any other annotation
				String annoClassName = annotation.getClass().getName();
				if (annoClassName.equals("javax.annotation.Nonnull")
						|| annoClassName.equals("javax.validation.constraints.NotNull")) {
					required = true;
				}
			}
		}

		Class<?> type = method.getParameterTypes()[index];
		if (type.getAnnotation(RestVar.class) != null) {
			return doComplexParameter(index, type, name, required);
		} else if (name == null && setter == null) {
			throw new HttlApiException("Missing parameter's name on position " + (index + 1), method);
		}

		ApiVarMeta meta = new ApiVarMeta(index, type, name, required, nullval, setter, marshaller, target);
		meta.variable = variable;
		return meta;
	}

	private static ApiVarMeta doComplexParameter(int index, Class<?> paramClazz, String paramName, boolean paramNotnull) {
		List<FieldApiVarMeta> list = new ArrayList<FieldApiVarMeta>();
		RestVar paramVar = paramClazz.getAnnotation(RestVar.class);
		StringBuilder name = new StringBuilder();
		if (paramName != null && !paramName.equals(RestVar.NULL_STRING_SURROGATE)) {
			name.append(paramName);
		}
		if (getRestVarName(paramVar) != null) {
			name.append(getRestVarName(paramVar));
		}
		Field[] fields = paramClazz.getDeclaredFields();
		for (Field field : fields) {
			String localName = field.getName();
			boolean notnull = false;
			String nullval = null;
			VarSetter<Object> setter = null;
			RestVar fieldVar = field.getAnnotation(RestVar.class);
			if (fieldVar != null) {
				notnull = fieldVar.required();
				if (!fieldVar.defval().equals(RestVar.NULL_STRING_SURROGATE)) {
					nullval = fieldVar.defval();
				}
				if (fieldVar.setter() != NoopParamSetter.class) {
					try {
						setter = fieldVar.setter().newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Cannot create " + fieldVar.setter().getName() + " instance ", x);
					}
				}
				if (getRestVarName(fieldVar) != null) {
					localName = getRestVarName(fieldVar);
				}
			}

			ApiVarMeta meta = new ApiVarMeta(index, field.getType(), name.toString() + localName, notnull, nullval, setter,
					null, VarTarget.QUERY);
			list.add(new FieldApiVarMeta(field, meta));
		}
		if (list.size() == 0) {
			throw new HttlApiException("No fields discovered", paramClazz);
		}
		ComplexMetaVarSetter setter = new ComplexMetaVarSetter(list.toArray(new FieldApiVarMeta[list.size()]));
		return new ApiVarMeta(index, paramClazz, paramName, paramNotnull, null, setter, null, VarTarget.QUERY);
	}

	/**
	 * Converts method getter/setter/adder name to property name
	 */
	private static String toPropName(String name) {
		if (name.startsWith("set") || name.startsWith("add") || name.startsWith("get")) {
			name = name.substring(3);
		}
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	/**
	 * Reason: 
	 * value() is default Java annotation's value storage - allows nice default storage syntax @RestVar("myparam")
	 * but it's name - 'value' is very misleading, because it's semantic is 'name'
	 * 
	 * Legal usage:
	 * @RestVar("myparam")
	 * @RestVar(name = "myparam")
	 * @RestVar(value = "myparam")
	 * 
	 * Illegal use: 
	 * @RestVar(name = "myparam", value = "wrong")
	 * 
	 * This also copes with Java annotation inability to store null as value requiring null surrogates usage
	 */
	private static String getRestVarName(RestVar var) {
		String value = var.value();
		boolean blankValue = value == null || value.length() == 0 || value.equals(RestVar.NULL_STRING_SURROGATE);

		String name = var.name();
		boolean blankName = name == null || name.length() == 0 || name.equals(RestVar.NULL_STRING_SURROGATE);

		if (blankName) {
			if (blankValue) {
				return null;
			} else {
				return value;
			}
		} else {
			if (blankValue) {
				return name;
			} else {
				throw new HttlApiException("Do not use both value='" + value + "' and name='" + name + "' in @RestVar",
						(Exception) null);
			}
		}
	}

	static class BuilderMeta {

		final Class<? extends HttlCallBuilder> interfacee;

		final Type returnType;

		final Map<Method, BuilderMethodMeta> methods;

		public BuilderMeta(Class<? extends HttlCallBuilder> interfacee, Type returnType,
				Map<Method, BuilderMethodMeta> methods) {
			this.interfacee = interfacee;
			this.returnType = returnType;
			this.methods = methods;
		}

	}

	static class BuilderMethodMeta {
		final Method method;
		final ApiVarMeta parameter;
		final boolean replace;

		public BuilderMethodMeta(Method method, ApiVarMeta parameter, boolean replace) {
			this.method = method;
			this.parameter = parameter;
			this.replace = replace;
		}

	}

	static class ApiMethodMeta {

		final Method method;
		final HttpMethod httpMethod;
		final String urlPath;
		final ApiVarMeta[] parameters;
		final ApiHeaderMeta[] headers;
		final Type returnType;
		final BuilderMeta builder;

		public ApiMethodMeta(Method method, HttpMethod httpMethod, String urlPath, ApiVarMeta[] parameters,
				ApiHeaderMeta[] headers, BuilderMeta builderMeta) {
			this.method = method;
			this.httpMethod = httpMethod;
			this.urlPath = urlPath;
			this.parameters = parameters;
			this.headers = headers;
			this.returnType = method.getGenericReturnType();
			this.builder = builderMeta;
		}

	}

	static enum VarTarget {
		PATH, QUERY, HEADER, BODY, //normal value parameters
		BLD_INTERCEPTOR, EXE_INTERCEPTOR, //REQ_INTERCEPTOR, RES_INTERCEPTOR, //
		REQ_MARSHALLER, RES_EXTRACTOR;//
	}

	static class ApiHeaderMeta {

		final String name;
		final String value;
		final boolean templated; //value is template

		public ApiHeaderMeta(String name, String value, boolean templated) {
			this.name = name;
			this.value = value;
			this.templated = templated;
		}

	}

	static class ApiVarMeta {

		final int index;
		final String name;
		final boolean killnull;
		final String nullval;
		final Type type;
		final VarSetter<Object> setter;
		final HttlMarshaller marshaller; //only for @Body
		VarTarget target;
		String variable; //@Body content type or placeholder for urlpath

		public ApiVarMeta(int index, Type type, String name, boolean killnull, String nullval, VarSetter<Object> setter,
				HttlMarshaller marshaller, VarTarget target) {
			this.index = index;
			this.type = type;
			this.name = name;
			this.killnull = killnull;
			this.nullval = nullval;
			this.setter = setter;
			this.marshaller = marshaller;
			this.target = target;
		}

		void setTarget(VarTarget target) {
			this.target = target;
		}

		void setTargetHeader(String headerName) {
			this.variable = headerName;
			this.target = VarTarget.HEADER;
		}

	}

}
