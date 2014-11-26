package net.anthavio.httl.transport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Future;

import net.anthavio.httl.HttlBody;
import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.ReaderInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHandlerExtensions;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.PerRequestConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.generators.InputStreamBodyGenerator;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;

/**
 * https://github.com/AsyncHttpClient/async-http-client
 * 
 * https://asynchttpclient.github.io/async-http-client/request.html
 * 
 * @author martin.vanek
 *
 */
public class NingAsyncTransport implements HttlTransport<String> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final NingAsyncConfig config;

	private final AsyncHttpClient client;

	public NingAsyncTransport(NingAsyncConfig config) {
		this.config = config;
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setAllowPoolingConnection(true);
		//https://asynchttpclient.github.io/async-http-client/auth.html
		AsyncHttpClientConfig ningConfig = builder.build();
		this.client = new AsyncHttpClient(new NettyAsyncHttpProvider(ningConfig), ningConfig);
	}

	@Override
	public HttlResponse call(HttlRequest request) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	static class MarshallingEntityWriter implements Request.EntityWriter {

		private final HttlRequest request;

		public MarshallingEntityWriter(HttlRequest request) {
			this.request = request;
		}

		@Override
		public void writeEntity(OutputStream out) throws IOException {
			HttlBodyMarshaller marshaller = request.getSender().getMarshaller();
			marshaller.marshall(request.getBody().getPayload(), request.getMediaType(), request.getCharset(), out);
		}

	}

	@Override
	public Future<String> call(HttlRequest request, HttlTransportCallback<String> callback) {

		RequestBuilder builder = new RequestBuilder(request.getMethod().name()).setUrl(request.getPathAndQuery());

		Multival<String> headers = request.getHeaders();
		for (String name : headers) {
			List<String> values = headers.get(name);
			for (String value : values) {
				builder.addHeader(name, value);
			}
		}

		HttlBody body = request.getBody();
		if (body != null) {
			switch (body.getType()) {
			case BYTES:
				builder.setBody((byte[]) body.getPayload());
				break;
			case STRING:
				builder.setBody((String) body.getPayload());
				break;
			case MARSHALL:
				builder.setBody(new MarshallingEntityWriter(request));
				break;
			case STREAM:
				builder.setBody(new InputStreamBodyGenerator((InputStream) body.getPayload()));
				break;
			case READER:
				builder.setBody(new InputStreamBodyGenerator(new ReaderInputStream((Reader) body.getPayload())));
				break;
			default:
				throw new IllegalArgumentException("Unsupported body type " + body);
			}

		}

		if (request.getReadTimeoutMillis() != null) {
			//builder.setrequestTimeoutInMs
			PerRequestConfig perReqCon = new PerRequestConfig();
			perReqCon.setRequestTimeoutInMs(5 * 1000);
			//perReqCon.setProxy(new ProxyServer(...));
			builder.setPerRequestConfig(perReqCon);
		}
		Request ningRequest = builder.addHeader("name", "value").setBody(new File("myUpload.avi")).build();
		AsyncHandler<String> handler = new CustomAsyncHandler<String>(request, callback);
		//AsyncHandlerExtensions
		try {
			Future<String> f = client.executeRequest(ningRequest, handler);
			return f;
		} catch (IOException iox) {
			//callback.onRequestFailure(request, iox);
			throw new HttlRequestException(iox);
		}

	}

	class CustomAsyncHandler<T> implements AsyncHandler<T>, AsyncHandlerExtensions {

		private final HttlRequest httlRequest;

		private final HttlTransportCallback<T> httlCallback;

		private final Response.ResponseBuilder builder = new Response.ResponseBuilder();
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		private NingAsyncResponse httlResponse;

		private HttpResponseStatus responseStatus;

		public CustomAsyncHandler(HttlRequest httlRequest, HttlTransportCallback<T> httlCallback) {
			this.httlRequest = httlRequest;
			this.httlCallback = httlCallback;
		}

		@Override
		public void onThrowable(Throwable t) {

		}

		/**
		 * AsyncHandlerExtensions
		 */
		@Override
		public void onRequestSent() {
			httlCallback.onRequestCompleted(httlRequest);
		}

		/**
		 * AsyncHandlerExtensions
		 */
		@Override
		public void onRetry() {

		}

		@Override
		public AsyncHandler.STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
			this.responseStatus = responseStatus;
			httlCallback.onResponseStatus(httlRequest, responseStatus.getStatusCode(), responseStatus.getStatusText());
			return STATE.CONTINUE;
		}

		@Override
		public AsyncHandler.STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
			Multival<String> httlHeaders = new Multival<String>();

			FluentCaseInsensitiveStringsMap responseHeaders = headers.getHeaders();
			Set<Entry<String, List<String>>> entrySet = responseHeaders.entrySet();
			for (Entry<String, List<String>> entry : entrySet) {
				httlHeaders.set(entry.getKey(), entry.getValue());
			}

			InputStream stream = null;
			httlResponse = new NingAsyncResponse(httlRequest, responseStatus.getStatusCode(), responseStatus.getStatusText(),
					httlHeaders, null);
			httlCallback.onResponseHeaders(httlResponse);

			return STATE.CONTINUE;
		}

		@Override
		public AsyncHandler.STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
			//ByteBuffer byteBuffer = bodyPart.getBodyByteBuffer();
			//bodyPart.writeTo(bytes);
			bytes.write(bodyPart.getBodyPartBytes());
			return STATE.CONTINUE;
		}

		@Override
		public T onCompleted() throws Exception {
			// Will be invoked once the response has been fully read or a ResponseComplete exception
			// has been thrown.
			// NOTE: should probably use Content-Encoding from headers
			return null;//bytes.toString("UTF-8");
		}

	}

	@Override
	public void close() {
		try {
			client.close();
		} catch (Exception x) {
			log.warn("Closing Ning AsyncHttpClient failed", x);
		}
	}

	@Override
	public NingAsyncConfig getConfig() {
		return config;
	}

}
