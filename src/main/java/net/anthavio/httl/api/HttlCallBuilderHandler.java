package net.anthavio.httl.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlExecutionFilter;
import net.anthavio.httl.HttlRequestBuilders.SenderBodyRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.api.HttlApiBuilder.ApiMethodMeta;
import net.anthavio.httl.api.HttlApiBuilder.ApiVarMeta;
import net.anthavio.httl.api.HttlApiBuilder.BuilderMethodMeta;

/**
 * Dynamic proxy for HttlCallBuilder extended interfaces
 * 
 * @author martin.vanek
 *
 */
public class HttlCallBuilderHandler implements InvocationHandler {

	private final HttlApiHandler<?> apiHandler;

	private final ApiMethodMeta methodMeta;

	private final SenderBodyRequestBuilder builder;

	private final Map<Method, BuilderMethodMeta> methodMetaMap;

	private final HttlBuilderVisitor builderInterceptor;
	private final HttlExecutionFilter executionInterceptor;
	private final HttlResponseExtractor<?> responseExtractor;

	public HttlCallBuilderHandler(HttlApiHandler<?> apiHandler, ApiMethodMeta methodMeta,
			SenderBodyRequestBuilder builder, HttlBuilderVisitor builderInterceptor,
			HttlExecutionFilter executionInterceptor, HttlResponseExtractor<?> responseExtractor) {
		this.apiHandler = apiHandler;
		this.methodMeta = methodMeta;
		this.methodMetaMap = methodMeta.builder.methods;
		this.builder = builder;
		this.builderInterceptor = builderInterceptor;
		this.executionInterceptor = executionInterceptor;
		this.responseExtractor = responseExtractor;

	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		String methodName = method.getName();
		//java.lang.Object methods...
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
		} else if (methodName.equals("execute") && args == null) {
			return apiHandler.complete(builder, methodMeta, builderInterceptor, executionInterceptor, responseExtractor);
		}

		BuilderMethodMeta methodMeta = methodMetaMap.get(method);
		ApiVarMeta paramMeta = methodMeta.parameter;
		Object arg = args[0];
		if (paramMeta.killnull && arg == null) {
			throw new IllegalArgumentException("Parameter " + paramMeta.name + " is null");
		}
		if (paramMeta.setter != null) {
			paramMeta.setter.set(arg, paramMeta.name, builder);
		} else {
			builder.param(methodMeta.replace, paramMeta.name, arg);
		}

		//for (Object arg : args) {
		//	ApiParamMeta metaParam = metaMethod.parameters[i];
		//}

		return proxy;
	}
}
