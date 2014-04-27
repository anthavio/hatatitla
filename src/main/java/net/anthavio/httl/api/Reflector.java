package net.anthavio.httl.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.HttpSender;
import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class Reflector {

	private static AsmParameterNameDiscoverer asmDisco = new AsmParameterNameDiscoverer();

	public static <T> T build(Class<T> apiInterface, HttpSender sender) {
		for (Method method : apiInterface.getDeclaredMethods()) {
			System.out.println(method.toGenericString());
			//methodToHandler.put(method, nameToHandler.get(Feign.configKey(method)));
		}
		Map<Method, MetaMethod> methods = processMethods(apiInterface);
		InvocationHandler handler = new ProxyInvocationHandler<T>(apiInterface, sender, methods);
		return (T) Proxy.newProxyInstance(apiInterface.getClassLoader(), new Class<?>[] { apiInterface }, handler);
	}

	private static Map<Method, MetaMethod> processMethods(Class<?> apiInterface) {
		Map<Method, MetaMethod> result = new HashMap<Method, Reflector.MetaMethod>();
		for (Method method : apiInterface.getDeclaredMethods()) {
			Operation operation = method.getAnnotation(Operation.class);
			if (operation == null) {
				String mname = method.getName();
				if ("equals".equals(mname) || "hashCode".equals(mname)) {
					continue;
				}
				//is it clever ???
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

			Map<String, MetaParam> metas = getMetaParams(method);

			int lastOpn = -1;
			while ((lastOpn = path.indexOf('{', lastOpn + 1)) != -1) {
				int lastCls = path.indexOf('}', lastOpn + 1);
				if (lastCls == -1) {
					throw new IllegalArgumentException("Unpaired bracket in path " + path);
				}

				String pathParam = path.substring(lastOpn + 1, lastCls);
				MetaParam mparam = metas.get(pathParam);
				if (mparam == null) {
					throw new IllegalArgumentException("Path parameter '" + pathParam + "' not found as '" + method.getName()
							+ "' method parameter");
				} else {
					mparam.setTarget(ParamTarget.PATH);
				}
			}

			Map<String, MetaParam> mheaders = getMetaHeaders(apiInterface, method);

			MetaMethod meta = new MetaMethod(method, httpMethod, path, null); //FIXME metas
			result.put(method, meta);
		}
		return result;
	}

	private static Map<String, MetaParam> getMetaHeaders(Class<?> clazz, Method method) {
		clazz.getAnnotation(Headers.class);

		method.getAnnotation(Headers.class);
		// TODO Auto-generated method stub
		return null;
	}

	private static Map<String, MetaParam> getMetaParams(Method method) {
		Map<String, MetaParam> result = new HashMap<String, Reflector.MetaParam>();
		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			String pname = getParameterName(i, method);
			MetaParam meta = new MetaParam(i, pname, types[i], ParamTarget.QUERY);
			result.put(pname, meta);
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
				name = "body";
			} else if (annotation.getClass().getName().equals("javax.inject.Named")) {
				try {
					Method mvalue = annotation.getClass().getMethod("value");
					name = (String) mvalue.invoke(annotation);
				} catch (Exception x) {
					//TODO log or throw
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
		private final Type returnType;

		public MetaMethod(Method method, HttpMethod httpMethod, String urlPath, MetaParam[] parameters) {
			this.method = method;
			this.httpMethod = httpMethod;
			this.urlPath = urlPath;
			this.parameters = parameters;
			this.returnType = method.getReturnType();
		}

	}

	static enum ParamTarget {
		PATH, QUERY, HEADER, BODY;
	}

	static class MetaParam {

		private final int index;
		private final String name;
		private final Type type;
		private ParamTarget target;

		public MetaParam(int index, String name, Type type, ParamTarget target) {
			this.index = index;
			this.name = name;
			this.type = type;
			this.target = target;
		}

		void setTarget(ParamTarget target) {
			this.target = target;
		}

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
			for (int i = 0; i < args.length; ++i) {
				MetaParam pmeta = mmeta.parameters[i];
				switch (pmeta.target) {
				case PATH:
				case HEADER:
				case QUERY:
				case BODY:

					//TODO replace in header 
				}

			}
			Multival params = null;
			Multival headers = null;
			SenderRequest request = new SenderRequest(sender, mmeta.httpMethod.getMethod(), mmeta.urlPath, params, headers);
			//meta
			sender.execute(request);
			//method.toGenericString();
			//return sender.;
			return null;
		}
	}

}
