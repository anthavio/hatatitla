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

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionFilter;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.api.HttlBody.NullSurrogateHttlBodyWriter;
import net.anthavio.httl.api.HttlCall.HttpMethod;
import net.anthavio.httl.api.HttlVar.NoopParamSetter;
import net.anthavio.httl.api.VarSetter.BeanMetaVarSetter;
import net.anthavio.httl.api.VarSetter.FieldApiVarMeta;
import net.anthavio.httl.util.HttlUtil;

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

	private Multival<String> headers;

	private Multival<String> params;

	public HttlApiBuilder(HttlSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("Null sender");
		}
		this.sender = sender;
		params = new Multival<String>();
	}

	/**
	 * Add this header into every request
	 */
	public HttlApiBuilder addHeader(String name, String value) {
		if (headers == null) {
			headers = new Multival<String>();
		}
		headers.add(name, value);
		return this;
	}

	/**
	 * Add this parameter into every request
	 */
	public HttlApiBuilder addParam(String name, String value) {
		if (params == null) {
			params = new Multival<String>();
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

	public static <T> T build(Class<T> apiInterface, HttlSender sender, Multival<String> headers, Multival<String> params) {
		final HttlApi httlApi = apiInterface.getAnnotation(HttlApi.class);

		final String urlPathPrefix = (httlApi != null) ? getRestApiUri(httlApi) : "";
		Map<Type, VarSetter<Object>> sharedSetters = new HashMap<Type, VarSetter<Object>>();
		if (httlApi != null) {
			//Initialize shared setters
			Class<? extends VarSetter>[] setters = httlApi.setters();
			if (setters.length != 0) {
				for (Class<? extends VarSetter> setterClass : setters) {
					ParameterizedType parametrized = (ParameterizedType) setterClass.getGenericInterfaces()[0];
					Type targetType = parametrized.getActualTypeArguments()[0];
					VarSetter<Object> setter;
					try {
						setter = setterClass.newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Failed to instantiate shared VarSetter " + setterClass, apiInterface);
					}
					sharedSetters.put(targetType, setter);
				}
			}
		}

		Map<Method, ApiMethodMeta> methods = doApiMethods(apiInterface, urlPathPrefix, sharedSetters);
		InvocationHandler handler = new HttlApiHandler<T>(apiInterface, sender, headers, params, methods);
		return (T) Proxy.newProxyInstance(apiInterface.getClassLoader(), new Class<?>[] { apiInterface }, handler);
	}

	private static Map<Method, ApiMethodMeta> doApiMethods(Class<?> apiInterface, String urlPathPrefix,
			Map<Type, VarSetter<Object>> sharedSetters) {
		Map<Method, ApiMethodMeta> metaMap = new HashMap<Method, ApiMethodMeta>();
		for (Method method : apiInterface.getDeclaredMethods()) {
			HttlCall operation = method.getAnnotation(HttlCall.class);
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
			if (urlPathPrefix != null) {
				urlPath = HttlUtil.joinUrlParts(urlPathPrefix, urlPath);
			}

			//@RestVar annotations
			ApiVarMeta[] params = doApiParameters(method, sharedSetters);
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
				if (header.parameter != null) {
					ApiVarMeta param = paramMap.get(header.parameter); //Templated @Header needs @Param with value
					if (param != null) {
						param.setTargetHeader(header.name);
					} else {
						throw new HttlApiException("Templated Header '" + header.parameter + "' not found as parameter", method);
					}
				}
				headerMap.put(header.name, header);
			}

			//Some sanity checks...

			ApiVarMeta body = paramMap.get(BODY);

			if (body != null) {
				if (http.getMethod().isBodyAllowed() == false) {
					throw new HttlApiException("Body not allowed on HTTP " + http.getMethod(), method);
				}

				if (body.variable == null && headerMap.get(HttlConstants.Content_Type) == null) {
					throw new HttlApiException("Content-Type not specified. Neither as a @Header nor as @Body parameter", method);
				}

				//maybe set priority instead of exception... maybe
				if (body.variable != null && headerMap.get(HttlConstants.Content_Type) != null) {
					throw new HttlApiException("Content-Type specified twice. As a @Header and as @Body parameter", method);
				}

			} else { // @Body param does not exist
				if (paramMap.get("#" + HttlBodyMarshaller.class.getSimpleName()) != null) {
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

			ApiVarMeta varMeta = doParameter(0, method, null, paramName);
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
		HttlHeaders cheaders = clazz.getAnnotation(HttlHeaders.class);
		if (cheaders != null && cheaders.value().length != 0) {
			for (String header : cheaders.value()) {
				if (header.indexOf('{') != -1 && header.indexOf('}') != -1) {
					throw new HttlApiException("Global headers cannot be templated: " + header, clazz);
				}
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new HttlApiException("Global header syntax error: " + header, clazz);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				ApiHeaderMeta metaHeader = new ApiHeaderMeta(name, value, null);
				headerList.add(metaHeader);
				headerMap.put(name, headerList.size() - 1);
			}
		}

		//Process Method declared (local) annotations now
		HttlHeaders mheaders = method.getAnnotation(HttlHeaders.class);
		if (mheaders != null && mheaders.value().length != 0) {
			for (String header : mheaders.value()) {
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new HttlApiException("Method header syntax error: " + header, method);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				String parameter = null;

				int idxOpn = value.indexOf('{');
				int idxCls = value.indexOf('}', idxOpn + 1);
				if (idxOpn != -1) {
					if (idxCls != -1) {
						parameter = value.substring(idxOpn + 1, idxCls);
					} else {
						throw new HttlApiException("Unpaired brackets in method header: " + header, method);
					}
				}
				ApiHeaderMeta metaHeader = new ApiHeaderMeta(name, value, parameter);
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

	private static ApiVarMeta[] doApiParameters(Method method, Map<Type, VarSetter<Object>> sharedSetters) {
		Map<String, ApiVarMeta> map = new HashMap<String, ApiVarMeta>();//only for duplicate name checks
		Class<?>[] types = method.getParameterTypes();
		List<ApiVarMeta> metaList = new ArrayList<ApiVarMeta>();
		for (int i = 0; i < types.length; i++) {
			Class<?> type = types[i];
			String name;
			ApiVarMeta meta;
			// interceptors/marshallers/extractors first - they need no annotation with name
			if (HttlBuilderVisitor.class.isAssignableFrom(type)) {
				name = "#" + HttlBuilderVisitor.class.getSimpleName() + "-" + i;
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.BLDR_VISITOR);
				metaList.add(meta);

			} else if (HttlExecutionFilter.class.isAssignableFrom(type)) {
				name = "#" + HttlExecutionFilter.class.getSimpleName() + "-" + i;
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.EXEC_FILTER);
				metaList.add(meta);

			} else if (HttlBodyWriter.class.isAssignableFrom(type)) {
				name = "#" + HttlBodyWriter.class.getSimpleName();
				if (map.containsKey(name)) { //TODO also add check if body writer is not defined via attribute
					throw new HttlApiException("Multiple HttlBodyWriter parameters found", method);
				}
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.BODY_WRITER);
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
				meta = new ApiVarMeta(i, type, name, true, null, null, null, VarTarget.RESP_EXTRACTOR);
				metaList.add(meta);

			} else {
				// normal parameters here...
				meta = doParameter(i, method, sharedSetters, null);
				if (map.containsKey(meta.name)) {
					throw new HttlApiException("Duplicate parameter named '" + meta.name + "' found", method);
				}
				map.put(meta.name, meta);
				metaList.add(meta);
			}

		}
		return metaList.toArray(new ApiVarMeta[metaList.size()]);
	}

	/*
		private static ApiVarMeta doApiParameter(int index, Method method) {
			Annotation[][] mannotations = method.getParameterAnnotations();
			if (mannotations.length == 0) {
				throw new HttlApiException("Cannot determine parameter's name on position " + (index + 1)
						+ ". No annotation present", method);
			}
			return doParameter(index, method, null);

		}
	*/
	private static ApiVarMeta doParameter(int index, Method method, Map<Type, VarSetter<Object>> sharedSetters,
			String defaultName) {

		String name = defaultName;
		boolean required = false;
		String nullval = null;
		VarSetter<Object> setter = null;
		HttlBodyWriter<Object> writer = null;
		String variable = null;
		VarTarget target = VarTarget.QUERY;

		Annotation[] pannotations = method.getParameterAnnotations()[index];
		for (Annotation annotation : pannotations) {
			if (annotation instanceof HttlVar) {
				HttlVar var = (HttlVar) annotation;
				name = getRestVarName(var);
				required = var.required();
				if (!var.defval().equals(HttlVar.NULL_STRING_SURROGATE)) {
					nullval = var.defval();
				}
				if (var.setter() != NoopParamSetter.class) {
					try {
						setter = var.setter().newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Cannot create " + var.setter().getName() + " instance ", x);
					}
				}

			} else if (annotation instanceof HttlBody) {
				HttlBody body = (HttlBody) annotation;
				name = BODY; //artificial parameter name
				target = VarTarget.BODY;
				if (!body.value().isEmpty()) {
					variable = body.value(); //Content-Type header
				}
				if (body.writer() != NullSurrogateHttlBodyWriter.class) {
					try {
						writer = body.writer().newInstance();
					} catch (Exception x) {
						throw new HttlApiException("Cannot create " + body.writer().getName() + " instance ", x);
					}
				}
			} else if (annotation.getClass().getName().equals("javax.inject.Named")) {
				try {
					Method mvalue = annotation.getClass().getMethod("value");
					name = (String) mvalue.invoke(annotation);
				} catch (Exception x) {
					throw new HttlApiException("Reflective invocation javax.inject.Named.value()", x);
				}
			} else { // Any other annotation
				String annoClassName = annotation.getClass().getName();
				if (annoClassName.equals("javax.annotation.Nonnull")
						|| annoClassName.equals("javax.validation.constraints.NotNull")) {
					required = true;
				}
			}
		}
		Class<?> paramClass = method.getParameterTypes()[index];

		if (setter != null) {
			// check that VarSetter<X> generic parameter type is compatible with parameter type
			Type setterArgType = ((ParameterizedType) setter.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
			if (isCompatible(paramClass, (Class<?>) setterArgType) == false) {
				throw new HttlApiException("Incompatible VarSetter<" + setterArgType + "> for parameter '" + name + "' type "
						+ paramClass.getSimpleName(), method);
			}
		} else {

			if (paramClass.getAnnotation(HttlVar.class) != null) {
				//Custom Bean with @RestVar class anotation
				return doBeanParameter(index, paramClass, name, required);

			} else if (Map.class.isAssignableFrom(paramClass)) {
				// Map are special
				return doMapParameter(index, paramClass, name, required);
			}
		}
		//still nothing -> try shared setter
		if (setter == null && sharedSetters != null) {
			setter = sharedSetters.get(paramClass);//can be null...

		}

		//fail if 
		if (name == null && setter == null) {
			throw new HttlApiException("Missing parameter's name on position " + (index + 1), method);
		}

		ApiVarMeta meta = new ApiVarMeta(index, paramClass, name, required, nullval, setter, writer, target);
		meta.variable = variable;
		return meta;
	}

	private static ApiVarMeta doMapParameter(int index, Class<?> paramClazz, String paramName, boolean paramNotnull) {
		return new ApiVarMeta(index, paramClazz, paramName, paramNotnull, null, VarSetter.MapVarSetter, null,
				VarTarget.QUERY);
	}

	private static ApiVarMeta doBeanParameter(int index, Class<?> paramClazz, String paramName, boolean paramNotnull) {
		List<FieldApiVarMeta> list = new ArrayList<FieldApiVarMeta>();
		HttlVar paramVar = paramClazz.getAnnotation(HttlVar.class);
		StringBuilder name = new StringBuilder();
		if (paramName != null && !paramName.equals(HttlVar.NULL_STRING_SURROGATE)) {
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
			HttlVar fieldVar = field.getAnnotation(HttlVar.class);
			if (fieldVar != null) {
				notnull = fieldVar.required();
				if (!fieldVar.defval().equals(HttlVar.NULL_STRING_SURROGATE)) {
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
		BeanMetaVarSetter setter = new BeanMetaVarSetter(paramClazz, list.toArray(new FieldApiVarMeta[list.size()]));
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
	private static String getRestVarName(HttlVar var) {
		return getAnnotationValue(var.value(), var.name(), "name");
	}

	private static String getRestApiUri(HttlApi api) {
		return getAnnotationValue(api.value(), api.uri(), "uri");
	}

	private static String getAnnotationValue(String value, String preferred, String preferredName) {
		boolean blankValue = value == null || value.length() == 0 || value.equals(HttlVar.NULL_STRING_SURROGATE);
		boolean blankName = preferred == null || preferred.length() == 0 || preferred.equals(HttlVar.NULL_STRING_SURROGATE);
		if (blankName) {
			if (blankValue) {
				return null;
			} else {
				return value;
			}
		} else {
			if (blankValue) {
				return preferred;
			} else {
				throw new HttlApiException("Do not use both value='" + value + "' and " + preferredName + "='" + preferred
						+ "' in annotation", (Exception) null);
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
		final Map<String, ApiHeaderMeta> headersMap;
		final BuilderMeta builder;

		public ApiMethodMeta(Method method, HttpMethod httpMethod, String urlPath, ApiVarMeta[] parameters,
				ApiHeaderMeta[] headers, BuilderMeta builderMeta) {
			this.method = method;
			this.httpMethod = httpMethod;
			this.urlPath = urlPath;
			this.parameters = parameters;
			this.headers = headers;
			this.headersMap = new HashMap<String, ApiHeaderMeta>();
			for (ApiHeaderMeta header : headers) {
				headersMap.put(header.name, header);
			}
			this.builder = builderMeta;

		}

	}

	static enum VarTarget {
		PATH, QUERY, HEADER, BODY, //normal value parameters
		BLDR_VISITOR, EXEC_FILTER, //REQ_INTERCEPTOR, RES_INTERCEPTOR, //
		BODY_WRITER, RESP_EXTRACTOR;//
	}

	static class ApiHeaderMeta {

		final String name;
		final String value;
		final String parameter; //value is {parameter}

		public ApiHeaderMeta(String name, String value, String parameter) {
			this.name = name;
			this.value = value;
			this.parameter = parameter;
		}

	}

	@SuppressWarnings("rawtypes")
	static class ApiVarMeta {

		final int index;
		final String name;
		final boolean killnull;
		final String nullval;
		final Type type;
		final VarSetter setter;
		final HttlBodyWriter writer; //only for @Body
		VarTarget target;
		String variable; //@Body content type or placeholder for urlpath

		public ApiVarMeta(int index, Type type, String name, boolean killnull, String nullval, VarSetter setter,
				HttlBodyWriter marshaller, VarTarget target) {
			this.index = index;
			this.type = type;
			this.name = name;
			this.killnull = killnull;
			this.nullval = nullval;
			this.setter = setter;
			this.writer = marshaller;
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

	public static boolean isCompatible(Class<?> fromClass, Class<?> intoClass) {
		if (fromClass.isPrimitive()) {
			//autoboxing rules apply here
			if (fromClass == Integer.TYPE && intoClass == Integer.class) {
				return true;
			} else if (fromClass == Long.TYPE && intoClass == Long.class) {
				return true;
			} else if (fromClass == Float.TYPE && intoClass == Float.class) {
				return true;
			} else if (fromClass == Double.TYPE && intoClass == Double.class) {
				return true;
			} else if (fromClass == Byte.TYPE && intoClass == Byte.class) {
				return true;
			} else if (fromClass == Character.TYPE && intoClass == Character.class) {
				return true;
			} else if (fromClass == Boolean.TYPE && intoClass == Boolean.class) {
				return true;
			} else if (fromClass == Short.TYPE && intoClass == Short.class) {
				return true;
			}
		} else {
			if (intoClass.isAssignableFrom(fromClass)) {
				return true;
			}
		}
		return false;
	}

}
