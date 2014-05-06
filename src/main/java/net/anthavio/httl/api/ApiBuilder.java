package net.anthavio.httl.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.anthavio.httl.Constants;
import net.anthavio.httl.HttpSender;
import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.RequestInterceptor;
import net.anthavio.httl.ResponseInterceptor;
import net.anthavio.httl.SenderBodyRequest;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.Param.DefaultParamSetter;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseExtractorFactory;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class ApiBuilder {

	public static ApiBuilder with(HttpSender sender) {
		return new ApiBuilder(sender);
	}

	private final HttpSender sender;

	private Multival headers = new Multival();

	private Multival params = new Multival();

	public ApiBuilder(HttpSender sender) {
		this.sender = sender;
	}

	public ApiBuilder addHeader(String name, Object value) {
		headers.add(name, value);
		return this;
	}

	public ApiBuilder addParam(String name, String value) {
		params.add(name, value);
		return this;
	}

	public <T> T build(Class<T> apiInterface) {
		if (headers.size() != 0 || params.size() != 0) {

			RequestInterceptor interceptor = new RequestInterceptor() {

				@Override
				public void onRequest(SenderRequest request) {

					for (String name : headers) {
						request.addHeader(name, headers.get(name));
					}

					for (String name : params) {
						request.addParameter(name, params.get(name));
					}

				}
			};
			sender.addRequestInterceptor(interceptor);
		}
		return build(apiInterface, sender);
	}

	public static <T> T build(Class<T> apiInterface, HttpSender sender) {
		Map<Method, MetaMethod> methods = processMethods(apiInterface);
		InvocationHandler handler = new ProxyInvocationHandler<T>(apiInterface, sender, methods);
		return (T) Proxy.newProxyInstance(apiInterface.getClassLoader(), new Class<?>[] { apiInterface }, handler);
	}

	static class ProxyInvocationHandler<T> implements InvocationHandler {

		private final Class<T> apiInterface;

		private final HttpSender sender;

		private Map<Method, MetaMethod> methods = new HashMap<Method, MetaMethod>();

		public ProxyInvocationHandler(Class<T> apiInterface, HttpSender sender, Map<Method, MetaMethod> methods) {
			this.apiInterface = apiInterface;
			this.sender = sender;
			this.methods = methods;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

			MetaMethod mmeta = methods.get(method);
			if (mmeta == null) {
				String methodName = method.getName();
				if (methodName.equals("toString") && args == null) {
					return toString();
				} else if (methodName.equals("equals") && args != null && args.length == 1) {
					if (args[0] != null && Proxy.isProxyClass(args[0].getClass())) {
						return equals(Proxy.getInvocationHandler(args[0]));
					} else {
						return false;
					}
				} else if (methodName.equals("hashCode") && args == null) {
					return hashCode();
				}
				//Kill others...
				throw new IllegalStateException("Metadata not found for " + method);
			}

			Multival headers = new Multival();
			MetaHeader[] mheaders = mmeta.headers;
			for (MetaHeader mheader : mheaders) {
				headers.add(mheader.name, mheader.value); //header value can be placeholder
			}
			Multival params = new Multival();
			String urlPath = mmeta.urlPath;

			SenderRequest request;
			if (mmeta.httpMethod.getMethod().canHaveBody()) {
				request = new SenderBodyRequest(sender, mmeta.httpMethod.getMethod(), urlPath, params, headers);
			} else {
				request = new SenderRequest(sender, mmeta.httpMethod.getMethod(), urlPath, params, headers);
			}

			RequestInterceptor requestInterceptor = null;
			ResponseInterceptor responseInterceptor = null;
			RequestBodyMarshaller requestMarshaller = null;
			ResponseBodyExtractor<?> responseExtractor = null;

			Object body = null;
			if (args != null) {
				for (int i = 0; i < args.length; ++i) {
					MetaParam pmeta = mmeta.parameters[i];
					Object arg = args[i];
					if (pmeta.setter != null) {
						//custom setter works it's own way
						pmeta.setter.set(arg, pmeta.name, request);
					} else {
						//default setters are hardcoded
						switch (pmeta.target) {
						case PATH:
							if (arg == null) {
								throw new IllegalArgumentException("Url path parameter '" + pmeta.name + "' must not be null");
							}
							//urlPath = urlPath.replace("{" + pmeta.name + "}", String.valueOf(args[i])); //somehow more effectively ?
							params.set("{" + pmeta.name + "}", arg);
							break;
						case HEADER:
							if (arg == null) {
								throw new IllegalArgumentException("Header parameter '" + pmeta.name + "' must not be null");
							}
							headers.set(pmeta.variable, arg); //replace header
							break;
						case QUERY:
							params.add(pmeta.name, arg);
							break;
						case BODY:
							if (pmeta.variable != null) {
								headers.set(Constants.Content_Type, pmeta.variable);
							}
							body = arg;
							break;
						case REQ_INTERCEPTOR:
							requestInterceptor = (RequestInterceptor) arg;
							break;
						case RES_INTERCEPTOR:
							responseInterceptor = (ResponseInterceptor) arg;
							break;
						case REQ_MARSHALLER:
							requestMarshaller = (RequestBodyMarshaller) arg;
							break;
						case RES_EXTRACTOR:
							responseExtractor = (ResponseBodyExtractor<?>) arg;
							break;
						default:
							throw new IllegalStateException("Unsuported " + pmeta.name + " parameter target " + pmeta.target);
						}
					}
				}
			}

			//custom marshaller parameter to process body...
			String marshalledBody = null;
			if (requestMarshaller != null && body != null) {
				marshalledBody = requestMarshaller.marshall(body);
			}

			if (mmeta.httpMethod.getMethod().canHaveBody()) {
				String contentType = headers.getFirst(Constants.Content_Type);
				if (marshalledBody != null) {
					((SenderBodyRequest) request).setBody(marshalledBody, contentType);
				} else if (body != null) {
					((SenderBodyRequest) request).setBody(body, contentType);
				}
			}

			if (requestInterceptor != null) {
				requestInterceptor.onRequest(request);
			}

			SenderResponse response = sender.execute(request);

			if (responseInterceptor != null) {
				responseInterceptor.onResponse(response);
			}

			//custom extractor parameter to process body...
			Object extractedBody = null;
			if (responseExtractor != null) {
				extractedBody = responseExtractor.extract(response);
			}

			return getReturn(mmeta.returnType, extractedBody, response);
		}

		private Object getReturn(Type type, Object extractedBody, SenderResponse response) throws IOException {

			if (type == void.class) {
				return null;

			} else if (type == SenderResponse.class) {
				return response;

			} else if (type == String.class) {
				return HttpHeaderUtil.readAsString(response);

			} else if (type == byte[].class) {
				return HttpHeaderUtil.readAsBytes(response);

			} else if (type == Reader.class) {
				return response.getReader();

			} else if (type == InputStream.class) {
				return response.getStream();

			} else {
				if (extractedBody != null && type == extractedBody.getClass()) {
					return extractedBody;
				}
				ResponseExtractorFactory factory = sender.getResponseExtractorFactory(response.getMediaType());
				if (factory == null) {
					throw new IllegalStateException("No ResponseExtractor for: '" + response.getMediaType() + "'");
				}
				return factory.getExtractor(response, type).extract(response);
			}
		}

		@Override
		public String toString() {
			return "ProxyInvocationHandler for " + apiInterface.getName() + " and " + sender;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((apiInterface == null) ? 0 : apiInterface.hashCode());
			result = prime * result + ((sender == null) ? 0 : sender.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ProxyInvocationHandler other = (ProxyInvocationHandler) obj;
			if (apiInterface == null) {
				if (other.apiInterface != null)
					return false;
			} else if (!apiInterface.equals(other.apiInterface))
				return false;
			if (sender == null) {
				if (other.sender != null)
					return false;
			} else if (!sender.equals(other.sender))
				return false;
			return true;
		}

	}

	private static Map<Method, MetaMethod> processMethods(Class<?> apiInterface) {
		Map<Method, MetaMethod> result = new HashMap<Method, MetaMethod>();
		for (Method method : apiInterface.getDeclaredMethods()) {
			Operation operation = method.getAnnotation(Operation.class);
			if (operation == null) {
				String mname = method.getName();
				if ("equals".equals(mname) || "hashCode".equals(mname)) {
					continue;
				}
				throw new IllegalArgumentException("Method " + method + " does not have @Operation annotation");
			}

			HttpMethod httpMethod = operation.method();
			String path = operation.value();
			if (httpMethod == HttpMethod.PATH) {
				//maybe nicer exceptions here
				String[] segments = path.split(" ");
				httpMethod = HttpMethod.valueOf(segments[0]);
				path = segments[1];
			}

			//@Param annotations
			MetaParam[] params = processParameters(method);
			Map<String, MetaParam> paramMap = new HashMap<String, ApiBuilder.MetaParam>();
			for (MetaParam param : params) {
				paramMap.put(param.name, param);
			}

			//search url path for {placehoders}
			int lastOpn = -1;
			while ((lastOpn = path.indexOf('{', lastOpn + 1)) != -1) {
				int lastCls = path.indexOf('}', lastOpn + 1);
				if (lastCls == -1) {
					throw new IllegalArgumentException("Unpaired bracket in path " + path);
				}

				String pathParam = path.substring(lastOpn + 1, lastCls);
				MetaParam param = paramMap.get(pathParam);
				if (param == null) {
					throw new IllegalArgumentException("Path placeholder '" + pathParam + "' not found as '" + method.getName()
							+ "' method parameter");
				} else {
					param.setTarget(ParamTarget.PATH);
				}
			}
			//XXX maybe some prevention against duplicate path/header placeholders

			//@Header annotations
			MetaHeader[] headers = processHeaders(apiInterface, method);
			Map<String, MetaHeader> headerMap = new HashMap<String, ApiBuilder.MetaHeader>();
			for (MetaHeader header : headers) {
				if (header.templated) {
					MetaParam param = paramMap.get(header.value); //Templated @Header needs @Param with value
					if (param != null) {
						param.setTargetHeader(header.name);
					} else {
						throw new IllegalArgumentException("Templated Header '" + header.value + "' not found as '"
								+ method.getName() + "' method parameter");
					}
				}
				headerMap.put(header.name, header);
			}

			//Some sanity checks...

			MetaParam body = paramMap.get(BODY);

			if (body != null) {
				if (httpMethod.getMethod().canHaveBody() == false) {
					throw new IllegalArgumentException("Body in not allowed on HTTP " + httpMethod.getMethod() + " on: " + method);
				}

				if (body.variable == null && headerMap.get(Constants.Content_Type) == null) {
					throw new IllegalArgumentException(
							"Content-Type not specified. Neither as a @Header nor as @Body parameter on: " + method);
				}

				//maybe set priority instead of exception... maybe
				if (body.variable != null && headerMap.get(Constants.Content_Type) != null) {
					throw new IllegalArgumentException("Content-Type specified twice. As a @Header and as @Body parameter on: "
							+ method);
				}

			} else { // no body param
				if (paramMap.get("#" + RequestBodyMarshaller.class.getSimpleName()) != null) {
					throw new IllegalArgumentException("Body is required when using RequestBodyMarshaller. Fix your '"
							+ method.getName() + "' method declaration on " + method.getDeclaringClass());
				}
			}

			// parameters not found as path or header placeholder are left as query parameters

			MetaMethod mmeta = new MetaMethod(method, httpMethod, path, params, headers);
			result.put(method, mmeta);
		}
		return result;
	}

	private final static String BODY = "#body";

	private static MetaHeader[] processHeaders(Class<?> clazz, Method method) {
		List<MetaHeader> headerList = new ArrayList<ApiBuilder.MetaHeader>();
		Map<String, Integer> headerMap = new HashMap<String, Integer>();

		//Process Type declared (global) annotations first
		Headers cheaders = clazz.getAnnotation(Headers.class);
		if (cheaders != null && cheaders.value().length != 0) {
			for (String header : cheaders.value()) {
				if (header.indexOf('{') != -1 && header.indexOf('}') != -1) {
					throw new IllegalArgumentException("Shared headers cannot be templated: " + header);
				}
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new IllegalArgumentException("Shared header syntax error: " + header);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				MetaHeader metaHeader = new MetaHeader(name, value, false);
				headerList.add(metaHeader);
				headerMap.put(name, headerList.size() - 1);
			}
		}

		//Process Method declared (local) annotations now
		Headers mheaders = method.getAnnotation(Headers.class);
		if (mheaders != null && mheaders.value().length != 0) {
			for (String header : mheaders.value()) {
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new IllegalArgumentException("Method header syntax error: " + header);
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
						throw new IllegalArgumentException("Unpaired brackets in method header: " + header);
					}
				}
				MetaHeader metaHeader = new MetaHeader(name, value, templated);
				Integer index = headerMap.get(name);
				if (index != null) {
					headerList.set(index, metaHeader); //replace shared if exists
				} else {
					headerList.add(metaHeader);
				}
			}
		}
		return headerList.toArray(new MetaHeader[headerList.size()]);
	}

	private static MetaParam[] processParameters(Method method) {
		Map<String, MetaParam> map = new HashMap<String, MetaParam>();
		Class<?>[] types = method.getParameterTypes();
		MetaParam[] result = new MetaParam[types.length];
		for (int i = 0; i < types.length; i++) {
			Class<?> type = types[i];
			String name;
			ParamTarget target;
			MetaParam meta;
			// interceptors/marshallers/extractors first - they need no annotation with name
			if (RequestInterceptor.class.isAssignableFrom(type)) {
				name = "#" + RequestInterceptor.class.getSimpleName();
				meta = new MetaParam(i, name, type, null, ParamTarget.REQ_INTERCEPTOR);

			} else if (ResponseInterceptor.class.isAssignableFrom(type)) {
				name = "#" + ResponseInterceptor.class.getSimpleName();
				meta = new MetaParam(i, name, type, null, ParamTarget.RES_INTERCEPTOR);

			} else if (RequestBodyMarshaller.class.isAssignableFrom(type)) {
				name = "#" + RequestBodyMarshaller.class.getSimpleName();
				meta = new MetaParam(i, name, type, null, ParamTarget.REQ_MARSHALLER);

			} else if (ResponseBodyExtractor.class.isAssignableFrom(type)) {
				name = "#" + ResponseBodyExtractor.class.getSimpleName();
				meta = new MetaParam(i, name, type, null, ParamTarget.RES_EXTRACTOR);

			} else {
				// normal parameters here...
				meta = parseParameter(i, method);
			}

			if (map.containsKey(meta.name)) {
				throw new IllegalArgumentException("Duplicate parameter named '" + meta.name + "' found");
			}
			result[i] = meta;
			map.put(meta.name, meta);
		}
		return result;
	}

	private static MetaParam parseParameter(int index, Method method) {
		Annotation[][] mannotations = method.getParameterAnnotations();
		if (mannotations.length == 0) {
			throw new IllegalArgumentException("Cannot determine parameter's name for method '" + method.getName()
					+ "' on position " + (index + 1) + ". No annotation present");
		}
		Class<?> type = method.getParameterTypes()[index];
		Annotation[] pannotations = mannotations[index];
		String name = null;
		ParamSetter setter = null;
		String variable = null;
		for (Annotation annotation : pannotations) {
			if (annotation instanceof Param) {
				Param param = (Param) annotation;
				name = param.value();

				if (param.setter() != DefaultParamSetter.class) {
					try {
						setter = param.setter().newInstance();
					} catch (Exception x) {
						throw new IllegalArgumentException("Cannot create " + param.setter().getName() + " instance ", x);
					}
				}

			} else if (annotation instanceof Body) {
				Body body = (Body) annotation;
				name = BODY;
				if (!body.value().isEmpty()) {
					variable = body.value(); //Content-Type header
				}
			} else if (annotation.getClass().getName().equals("javax.inject.Named")) {
				try {
					Method mvalue = annotation.getClass().getMethod("value");
					name = (String) mvalue.invoke(annotation);
				} catch (Exception x) {
					throw new IllegalStateException("Reflective invocation javax.inject.Named.value()", x);
				}
			}
		}

		if (name == null) {
			throw new IllegalArgumentException("Cannot determine parameter's name for method " + method.getName()
					+ " on position " + (index + 1) + ". No annotation found @Param, @Named,...");
		} else if (name.isEmpty()) {
			throw new IllegalArgumentException("Blank parameter's name for method '" + method.getName() + "' on position "
					+ (index + 1));
		}

		ParamTarget target = name.equals(BODY) ? ParamTarget.BODY : ParamTarget.QUERY;
		MetaParam meta = new MetaParam(index, name, type, setter, target);
		meta.variable = variable;
		return meta;
	}

	static class MetaMethod {

		private final Method method;
		private final HttpMethod httpMethod;
		private final String urlPath;
		private final MetaParam[] parameters;
		private final MetaHeader[] headers;
		private final Type returnType;

		public MetaMethod(Method method, HttpMethod httpMethod, String urlPath, MetaParam[] parameters, MetaHeader[] headers) {
			this.method = method;
			this.httpMethod = httpMethod;
			this.urlPath = urlPath;
			this.parameters = parameters;
			this.headers = headers;
			this.returnType = method.getGenericReturnType();
		}

	}

	static enum ParamTarget {
		PATH, QUERY, HEADER, BODY, //normal value parameters
		REQ_INTERCEPTOR, RES_INTERCEPTOR, REQ_MARSHALLER, RES_EXTRACTOR;
	}

	static class MetaHeader {

		private final String name;
		private final String value;
		private final boolean templated;

		public MetaHeader(String name, String value, boolean templated) {
			this.name = name;
			this.value = value;
			this.templated = templated;
		}

	}

	static class MetaParam {

		private final int index;
		private final String name;
		private final Type type;
		private final ParamSetter setter;
		private ParamTarget target;
		private String variable;

		public MetaParam(int index, String name, Type type, ParamSetter setter, ParamTarget target) {
			this.index = index;
			this.name = name;
			this.type = type;
			this.setter = setter;
			this.target = target;
		}

		void setTarget(ParamTarget target) {
			this.target = target;
		}

		void setTargetHeader(String headerName) {
			this.variable = headerName;
			this.target = ParamTarget.HEADER;
		}

	}

}
