package net.anthavio.httl.api;

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
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;

/**
 * 
 * @author martin.vanek
 *
 */
public class Reflector {

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

			Multival headers = new Multival();
			MetaHeader[] mheaders = mmeta.headers;
			for (MetaHeader mheader : mheaders) {
				headers.add(mheader.name, mheader.value); //header value can be placeholder
			}
			RequestInterceptor requestInterceptor = null;
			ResponseInterceptor responseInterceptor = null;
			RequestBodyMarshaller requestMarshaller = null;
			ResponseBodyExtractor<?> responseExtractor = null;

			Multival params = new Multival();
			String urlPath = mmeta.urlPath;
			Object body = null;
			for (int i = 0; i < args.length; ++i) {
				MetaParam pmeta = mmeta.parameters[i];
				Object arg = args[i];
				switch (pmeta.target) {
				case PATH:
					if (arg == null) {
						throw new IllegalArgumentException("Url path parameter '" + pmeta.name + "' must not be null");
					}
					urlPath = urlPath.replace("{" + pmeta.name + "}", String.valueOf(args[i])); //somehow more effectively ?
					break;
				case HEADER:
					if (arg == null) {
						throw new IllegalArgumentException("Header parameter '" + pmeta.name + "' must not be null");
					}
					headers.set(pmeta.headerName, arg); //replace header
					break;
				case QUERY:
					params.add(pmeta.name, arg);
					break;
				case BODY:
					body = args[i];
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

			if (requestMarshaller != null) {
				String marshalledBody = requestMarshaller.marshall(body);
			}

			SenderRequest request;
			if (mmeta.httpMethod.getMethod().canHaveBody()) {
				SenderBodyRequest brequest = new SenderBodyRequest(sender, mmeta.httpMethod.getMethod(), urlPath, params,
						headers);
				String contentType = headers.getFirst(Constants.Content_Type);
				brequest.setBody(body, contentType);
				request = brequest;
			} else {
				request = new SenderRequest(sender, mmeta.httpMethod.getMethod(), urlPath, params, headers);
			}

			if (requestInterceptor != null) {
				requestInterceptor.onRequest(request);
			}

			SenderResponse response = sender.execute(request);

			if (responseInterceptor != null) {
				responseInterceptor.onResponse(response);
			}

			Object extractedBody = null;
			if (responseExtractor != null) {
				extractedBody = responseExtractor.extract(response);
			}

			if (mmeta.returnType == Void.class) {
				return null;
			} else if (mmeta.returnType == SenderResponse.class) {
				return response;
			} else {
				if (extractedBody != null && mmeta.returnType.isAssignableFrom(extractedBody.getClass())) {
					return extractedBody;
				}
				return sender.extract(response, mmeta.returnType);
			}
		}
	}

	public static <T> T build(Class<T> apiInterface, HttpSender sender) {
		Map<Method, MetaMethod> methods = processMethods(apiInterface);
		InvocationHandler handler = new ProxyInvocationHandler<T>(apiInterface, sender, methods);
		return (T) Proxy.newProxyInstance(apiInterface.getClassLoader(), new Class<?>[] { apiInterface }, handler);
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

			MetaParam[] mparams = getMetaParams(method);
			Map<String, MetaParam> mpmap = new HashMap<String, Reflector.MetaParam>();
			for (MetaParam mparam : mparams) {
				mpmap.put(mparam.name, mparam);
			}

			//path parameters
			int lastOpn = -1;
			while ((lastOpn = path.indexOf('{', lastOpn + 1)) != -1) {
				int lastCls = path.indexOf('}', lastOpn + 1);
				if (lastCls == -1) {
					throw new IllegalArgumentException("Unpaired bracket in path " + path);
				}

				String pathParam = path.substring(lastOpn + 1, lastCls);
				MetaParam mparam = mpmap.get(pathParam);
				if (mparam == null) {
					throw new IllegalArgumentException("Path placeholder '" + pathParam + "' not found as '" + method.getName()
							+ "' method parameter");
				} else {
					mparam.setTarget(ParamTarget.PATH);
				}
			}
			//XXX maybe some prevention against duplicate path/header placeholders

			//header parameters
			MetaHeader[] mheaders = getMetaHeaders(apiInterface, method);
			for (MetaHeader mheader : mheaders) {
				if (mheader.parametrized) {
					MetaParam mparam = mpmap.get(mheader.value);
					if (mparam == null) {
						throw new IllegalArgumentException("Header placeholder '" + mheader.value + "' not found as '"
								+ method.getName() + "' method parameter");
					} else {
						mparam.setTargetHeader(mheader.name);
					}
				}
			}

			if (httpMethod.getMethod().canHaveBody() == false && mpmap.get("#body") != null) {
				throw new IllegalArgumentException("Parameter body in not allowed on HTTP " + httpMethod.getMethod());
			}

			if (mpmap.get("#" + RequestBodyMarshaller.class.getSimpleName()) != null && mpmap.get("#body") == null) {
				throw new IllegalArgumentException("Parameter body is required when using RequestBodyMarshaller");
			}

			// parameters not found as path or header placeholder are left as query parameters

			MetaMethod mmeta = new MetaMethod(method, httpMethod, path, mparams, mheaders);
			result.put(method, mmeta);
		}
		return result;
	}

	private static MetaHeader[] getMetaHeaders(Class<?> clazz, Method method) {
		List<MetaHeader> headerList = new ArrayList<Reflector.MetaHeader>();
		Map<String, Integer> headerMap = new HashMap<String, Integer>();

		Headers cheaders = clazz.getAnnotation(Headers.class);
		if (cheaders != null && cheaders.value().length != 0) {
			for (String header : cheaders.value()) {
				if (header.indexOf('{') != -1 && header.indexOf('}') != -1) {
					throw new IllegalArgumentException("Global headers cannot be parametrized: " + header);
				}
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new IllegalArgumentException("Wrong global header syntax: " + header);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				MetaHeader metaHeader = new MetaHeader(name, value, false);
				headerList.add(metaHeader);
				headerMap.put(name, headerList.size() - 1);
			}
		}

		Headers mheaders = method.getAnnotation(Headers.class);
		if (mheaders != null && mheaders.value().length != 0) {
			for (String header : mheaders.value()) {
				int idx = header.indexOf(':');
				if (idx == -1 || idx > header.length() - 2) {
					throw new IllegalArgumentException("Wrong method header syntax: " + header);
				}
				String name = header.substring(0, idx).trim();
				String value = header.substring(idx + 1).trim();
				boolean parametrized = false;

				int idxOpn = value.indexOf('{');
				int idxCls = value.indexOf('}', idxOpn + 1);
				if (idxOpn != -1) {
					if (idxCls != -1) {
						value = value.substring(idxOpn + 1, idxCls);
						parametrized = true;
					} else {
						throw new IllegalArgumentException("Unpaired brackets in method header: " + header);
					}
				}
				MetaHeader metaHeader = new MetaHeader(name, value, parametrized);
				Integer index = headerMap.get(name);
				if (index != null) {
					headerList.set(index, metaHeader); //replace global if exists
				} else {
					headerList.add(metaHeader);
				}
			}
		}
		return headerList.toArray(new MetaHeader[headerList.size()]);
	}

	private static MetaParam[] getMetaParams(Method method) {
		Map<String, MetaParam> map = new HashMap<String, Reflector.MetaParam>();
		Class<?>[] ptypes = method.getParameterTypes();
		MetaParam[] result = new MetaParam[ptypes.length];
		for (int i = 0; i < ptypes.length; i++) {
			Class<?> ptype = ptypes[i];
			String pname;
			ParamTarget target;
			// interceptors/marshallers/extractors first - they need no annotation with name
			if (ptype.isAssignableFrom(RequestInterceptor.class)) {
				pname = "#" + RequestInterceptor.class.getSimpleName();
				target = ParamTarget.REQ_INTERCEPTOR;

			} else if (ptype.isAssignableFrom(ResponseInterceptor.class)) {
				pname = "#" + ResponseInterceptor.class.getSimpleName();
				target = ParamTarget.RES_INTERCEPTOR;

			} else if (ptype.isAssignableFrom(RequestBodyMarshaller.class)) {
				pname = "#" + RequestBodyMarshaller.class.getSimpleName();
				target = ParamTarget.REQ_MARSHALLER;

			} else if (ptype.isAssignableFrom(ResponseBodyExtractor.class)) {
				pname = "#" + ResponseBodyExtractor.class.getSimpleName();
				target = ParamTarget.RES_EXTRACTOR;

			} else {
				// normal parameters here...
				pname = getParameterName(i, method);
				if (map.containsKey(pname)) {
					throw new IllegalArgumentException("Duplicate parameter named '" + pname + "' found");
				}
				if (pname.equals("#body")) {
					target = ParamTarget.BODY;
				} else {
					target = ParamTarget.QUERY;
				}
			}
			MetaParam pmeta = new MetaParam(i, pname, ptype, target);
			result[i] = pmeta;
			map.put(pname, pmeta);
		}
		return result;
	}

	private static String getParameterName(int index, Method method) {
		Annotation[][] mannotations = method.getParameterAnnotations();
		if (mannotations.length == 0) {
			throw new IllegalArgumentException("Cannot determine parameter's name for method " + method.getName()
					+ " on position " + (index + 1) + ". No annotation present");
		}
		Annotation[] pannotations = mannotations[index];
		String name = null;
		for (Annotation annotation : pannotations) {
			if (annotation instanceof Param) {
				name = ((Param) annotation).value();
			} else if (annotation instanceof Body) {
				name = "#body"; // hardcoded paramater name
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
					+ " on position " + (index + 1) + ". No annotation found @Param, @Named");
		}
		return name;
	}

	static class MetaMethod {

		private final Method method;
		private final HttpMethod httpMethod;
		private final String urlPath;
		private final MetaParam[] parameters;
		private final MetaHeader[] headers;
		private final Class<?> returnType;

		public MetaMethod(Method method, HttpMethod httpMethod, String urlPath, MetaParam[] parameters, MetaHeader[] headers) {
			this.method = method;
			this.httpMethod = httpMethod;
			this.urlPath = urlPath;
			this.parameters = parameters;
			this.headers = headers;
			this.returnType = method.getReturnType();
		}

	}

	static enum ParamTarget {
		PATH, QUERY, HEADER, BODY, //normal value parameters
		REQ_INTERCEPTOR, RES_INTERCEPTOR, REQ_MARSHALLER, RES_EXTRACTOR;
	}

	static class MetaHeader {

		private final String name;
		private final String value;
		private final boolean parametrized;

		public MetaHeader(String name, String value, boolean parametrized) {
			this.name = name;
			this.value = value;
			this.parametrized = parametrized;
		}

	}

	static class MetaParam {

		private final int index;
		private final String name;
		private final Type type;
		private ParamTarget target;
		private String headerName;

		public MetaParam(int index, String name, Type type, ParamTarget target) {
			this.index = index;
			this.name = name;
			this.type = type;
			this.target = target;
		}

		void setTarget(ParamTarget target) {
			this.target = target;
		}

		void setTargetHeader(String headerName) {
			this.headerName = headerName;
			this.target = ParamTarget.HEADER;
		}

	}

}
