package net.anthavio.httl.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Map;

import net.anthavio.httl.HttlBuilderInterceptor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionChain;
import net.anthavio.httl.HttlExecutionInterceptor;
import net.anthavio.httl.HttlMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestBuilders.SenderBodyRequestBuilder;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlSender.Parameters;
import net.anthavio.httl.api.HttlApiBuilder.ApiHeaderMeta;
import net.anthavio.httl.api.HttlApiBuilder.ApiMethodMeta;
import net.anthavio.httl.api.HttlApiBuilder.ApiVarMeta;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 * @param <T>
 */
public class HttlApiHandler<T> implements InvocationHandler {

	private final Class<T> apiInterface;

	private final HttlSender sender;

	private final Map<Method, ApiMethodMeta> methodMap;

	private final HttpHeaders headers; //static global headers

	private final Parameters params; //static global parameters

	public HttlApiHandler(Class<T> apiInterface, HttlSender sender, HttpHeaders headers, Parameters params,
			Map<Method, ApiMethodMeta> methods) {
		this.apiInterface = apiInterface;
		this.sender = sender;
		this.methodMap = methods;
		this.headers = headers;
		this.params = params;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		ApiMethodMeta metaMethod = methodMap.get(method);
		if (metaMethod == null) {
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
			}
			//Kill others not having annotation
			throw new IllegalStateException("Metadata not found for " + method);
		}

		SenderBodyRequestBuilder builder = new SenderBodyRequestBuilder(sender, metaMethod.httpMethod.getMethod(),
				metaMethod.urlPath);

		//Class declared headers
		ApiHeaderMeta[] mheaders = metaMethod.headers;
		for (ApiHeaderMeta mheader : mheaders) {
			if (!mheader.templated) { //Skip templated as the are populated from arguments
				builder.header(mheader.name, mheader.value);
			}
		}

		HttlMarshaller marshaller = null;

		HttlBuilderInterceptor builderInterceptor = null;
		HttlExecutionInterceptor executionInterceptor = null;
		HttlResponseExtractor<?> extractor = null;

		Object body = null;
		if (args != null) {
			for (int i = 0; i < args.length; ++i) {
				ApiVarMeta metaVar = metaMethod.parameters[i];
				Object arg = args[i];
				if (arg == null) {
					if (metaVar.killnull) {
						throw new HttlRequestException(((metaVar.name != null) ? "Argument " + metaVar.name : "Complex argument")
								+ " on position " + (i + 1) + " is null");
					} else if (metaVar.nullval != null) {
						arg = metaVar.nullval; //risky - different Class...?
					}
				}

				if (metaVar.setter != null) {
					//custom setter works it's own way
					metaVar.setter.set(arg, metaVar.name, builder);
				} else {
					//default setters are hardcoded here...
					switch (metaVar.target) {
					case QUERY:
						builder.param(metaVar.name, arg);
						break;
					case BODY:
						if (metaVar.variable != null) {
							builder.setHeader(HttlConstants.Content_Type, metaVar.variable); //replace header if exists
						}
						body = arg; //can be null
						break;
					case HEADER:
						if (arg == null) {
							throw new IllegalArgumentException("Header parameter '" + metaVar.name + "' value is null");
						}
						builder.header(metaVar.variable, String.valueOf(arg)); //replace header value
						break;
					case PATH:
						builder.param("{" + metaVar.name + "}", arg);
						break;
					case BLD_INTERCEPTOR:
						builderInterceptor = (HttlBuilderInterceptor) arg;
						break;
					case EXE_INTERCEPTOR:
						executionInterceptor = (HttlExecutionInterceptor) arg;
						break;
					/*
					case REQ_INTERCEPTOR:
						requestInterceptor = (RequestInterceptor) arg;
						break;
					case RES_INTERCEPTOR:
						responseInterceptor = (ResponseInterceptor) arg;
						break;
					*/
					case REQ_MARSHALLER:
						marshaller = (HttlMarshaller) arg;
						break;
					case RES_EXTRACTOR:
						extractor = (HttlResponseExtractor<?>) arg;
						break;
					default:
						throw new IllegalStateException("Unsuported " + metaVar.name + " parameter target " + metaVar.target);
					}
				}
			}
		}

		if (headers != null) {
			for (String name : headers) {
				builder.header(name, headers.get(name));
			}
		}

		if (params != null) {
			for (String name : params) {
				builder.param(name, params.get(name));
			}
		}

		if (body != null) {
			String[] contentType = HttlRequest.digContentType(builder.getHeaders(), sender.getConfig());
			if (contentType[0] == null) {
				throw new HttlRequestException("Content-Type header not specified");
			}
			if (marshaller != null) { //custom marshaller to build request payload body...
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				marshaller.write(body, baos, Charset.forName(contentType[1]));
				builder.body(baos.toByteArray(), contentType[0] + "; charset=" + contentType[1]);
			} else {
				builder.body(body, contentType[0] + "; charset=" + contentType[1]);
			}
		}

		if (metaMethod.builder == null) {
			return complete(builder, metaMethod, builderInterceptor, executionInterceptor, extractor);
		} else {
			//return OperationBuilder proxy
			HttlCallBuilderHandler handler = new HttlCallBuilderHandler(this, metaMethod, builder, builderInterceptor,
					executionInterceptor, extractor);
			return Proxy.newProxyInstance(metaMethod.builder.interfacee.getClassLoader(),
					new Class<?>[] { metaMethod.builder.interfacee }, handler);
		}

	}

	protected Object complete(SenderBodyRequestBuilder builder, ApiMethodMeta metaMethod,
			HttlBuilderInterceptor builderInterceptor, HttlExecutionInterceptor executionInterceptor,
			HttlResponseExtractor<?> responseExtractor) throws IOException {

		if (builderInterceptor != null) {
			builderInterceptor.onBuild(builder);
		}

		HttlRequest request = builder.build();

		HttlResponse response;
		if (executionInterceptor != null) {
			response = new ApiHandlerChain(executionInterceptor).next(request);
		} else {
			response = sender.execute(request);
		}

		//custom extractor parameter to process body...
		if (responseExtractor != null) {
			/*
			Object extract = responseExtractor.extract(response);
			if(extract!=null) {
				if(extract.getClass().isAssignableFrom(metaMethod.returnType)) {
					//damnit!!!
				}
			}
			*/
			//This will work only for direct ResponseExtractor interface implementation...
			/*
			Type typeArg = ((ParameterizedType) responseExtractor.getClass().getGenericInterfaces()[0])
					.getActualTypeArguments()[0];
			if (typeArg != metaMethod.returnType) {
				throw new HttlProcessingException("Incompatible ResponseExtractor product: " + typeArg + " for return type: "
						+ metaMethod.returnType);
			}
			*/
			return responseExtractor.extract(response);
		}

		if (metaMethod.builder != null) {
			return extract(metaMethod.builder.returnType, response);
		} else {
			return extract(metaMethod.returnType, response);
		}

	}

	private Object extract(Type returnType, HttlResponse response) throws IOException {

		if (returnType == void.class) {
			return null;

		} else if (returnType == HttlResponse.class) {
			return response;

		} else if (returnType == String.class) {
			return HttpHeaderUtil.readAsString(response);

		} else if (returnType == byte[].class) {
			return HttpHeaderUtil.readAsBytes(response);

		} else if (returnType == Reader.class) {
			return response.getReader();

		} else if (returnType == InputStream.class) {
			return response.getStream();

		} else {
			//maybe better checking here...
			return sender.extract(response, returnType).getPayload();
		}
	}

	private class ApiHandlerChain implements HttlExecutionChain {

		private HttlExecutionInterceptor interceptor;

		public ApiHandlerChain(HttlExecutionInterceptor interceptor) {
			this.interceptor = interceptor;
		}

		@Override
		public HttlResponse next(HttlRequest request) throws IOException {
			if (interceptor != null) {
				HttlExecutionInterceptor interceptor = this.interceptor;
				this.interceptor = null;
				return interceptor.intercept(request, this);
			} else {
				return sender.execute(request);
			}
		}

	}

	@Override
	public String toString() {
		return "ApiInvocationHandler for " + apiInterface.getName() + " and " + sender;
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
		HttlApiHandler other = (HttlApiHandler) obj;
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
