package net.anthavio.httl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;

import net.anthavio.httl.SenderBodyRequest.FakeStream;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.ConnectionPoolTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.util.EncodingUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Sender extends HttpSender {

	private final HttpClient httpClient;

	private final HttpClient3Config config;

	public HttpClient3Sender(String baseUrl) {
		this(new HttpClient3Config(baseUrl));
	}

	public HttpClient3Sender(HttpClient3Config config) {
		super(config);
		this.config = config;
		this.httpClient = config.buildHttpClient();
	}

	@Override
	public HttpClient3Config getConfig() {
		return config;
	}

	@Override
	public void close() {
		fireOnCloseSenderInterceptors();
		try {
			if (httpClient.getHttpConnectionManager() instanceof MultiThreadedHttpConnectionManager) {
				MultiThreadedHttpConnectionManager connectionManager = (MultiThreadedHttpConnectionManager) httpClient
						.getHttpConnectionManager();
				connectionManager.closeIdleConnections(0); //shutdown do not empty connection pool
				connectionManager.shutdown();
			}
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}

	/**
	 * Backdoor
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}

	@Override
	public HttpClient3Response doExecute(SenderRequest request, String path, String query) throws IOException {

		HttpMethodBase httpMethod;
		switch (request.getMethod()) {
		case GET:
			httpMethod = new GetMethod(path);
			break;
		case DELETE:
			httpMethod = new DeleteMethod(path);
			break;
		case HEAD:
			httpMethod = new HeadMethod(path);
			break;
		case OPTIONS:
			httpMethod = new OptionsMethod(path);
			break;
		case POST:
			PostMethod httpPost = new PostMethod(path);
			RequestEntity entityPost = buildEntity(request, query);
			httpPost.setRequestEntity(entityPost);
			httpMethod = httpPost;
			break;
		case PUT:
			PutMethod httpPut = new PutMethod(path);
			RequestEntity entityPut = buildEntity(request, query);
			httpPut.setRequestEntity(entityPut);
			httpMethod = httpPut;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		Multival headers = request.getHeaders();
		if (headers != null && headers.size() != 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					httpMethod.addRequestHeader(name, value);
				}
			}
		}

		if (request.getReadTimeoutMillis() != null) {
			httpMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, request.getReadTimeoutMillis());
		}
		//cannot be set globally in configuration
		httpMethod.setFollowRedirects(config.getFollowRedirects());

		if (config.getGzipRequest()) {
			httpMethod.addRequestHeader(Constants.Accept_Encoding, "gzip, deflate");
		}

		if (request.hasBody()) {
			String contentType = request.getFirstHeader(Constants.Content_Type);
			if (contentType == null) {
				throw new IllegalArgumentException("Request with body must have Content-Type header specified");
			}
			//add charset into Content-Type header if missing
			int idxCharset = contentType.indexOf("charset=");
			if (idxCharset == -1) {
				contentType = contentType + "; charset=" + config.getCharset();
				httpMethod.setRequestHeader(Constants.Content_Type, contentType);
			}
		}

		if (request.getFirstHeader(Constants.Accept) == null && config.getDefaultAccept() != null) {
			httpMethod.addRequestHeader(Constants.Accept, config.getDefaultAccept());
		}

		if (request.getFirstHeader(Constants.Accept_Charset) == null) {
			httpMethod.addRequestHeader(Constants.Accept_Charset, config.getEncoding());
		}

		int statusCode = call(httpMethod);

		Header[] responseHeaders = httpMethod.getResponseHeaders();

		Multival outHeaders = new Multival();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}
		StatusLine statusLine = httpMethod.getStatusLine();

		InputStream responseStream = httpMethod.getResponseBodyAsStream();

		HttpClient3Response response = new HttpClient3Response(statusCode, statusLine.getReasonPhrase(), outHeaders,
				responseStream, httpMethod);
		return response;
	}

	private RequestEntity buildEntity(SenderRequest request, String query) throws IOException {
		String contentType = request.getFirstHeader(Constants.Content_Type);
		Object[] type = HttpHeaderUtil.splitContentType(contentType, config.getCharset());
		String mimeType = (String) type[0];
		Charset charset = (Charset) type[1];

		RequestEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((SenderBodyRequest) request).getBodyStream();
			if (stream instanceof FakeStream) {
				FakeStream fake = (FakeStream) stream;
				if (fake.getValue() instanceof String) {
					entity = new StringRequestEntity((String) fake.getValue(), null, charset.name());
				} else {
					RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
					if (marshaller == null) {
						throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
					}
					entity = new ObjectEntity(fake.getValue(), charset, marshaller, fake.isStreaming());
				}
			} else {//plain InputStream
				entity = new InputStreamRequestEntity(stream);
			}
		} else if (query != null && query.length() != 0) {
			entity = new ByteArrayRequestEntity(EncodingUtil.getBytes(query, config.getEncoding()),
					PostMethod.FORM_URL_ENCODED_CONTENT_TYPE);
		} else {
			logger.debug("Body request does not have any parameters or body");
			entity = new StringRequestEntity("", null, config.getEncoding());
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	protected int call(HttpMethodBase httpRequest) throws IOException {
		try {
			return this.httpClient.executeMethod(httpRequest);
		} catch (Exception x) {
			//connection might be already open so release request
			httpRequest.releaseConnection();
			//now try to 
			if (x instanceof ConnectionPoolTimeoutException) {
				ConnectException cx = new ConnectException("Pool timeout " + config.getPoolAcquireTimeoutMillis() + " ms");
				cx.setStackTrace(x.getStackTrace());
				throw cx;
			} else if (x instanceof ConnectTimeoutException) {
				ConnectException cx = new ConnectException("Connect timeout " + config.getConnectTimeoutMillis() + " ms");
				cx.setStackTrace(x.getStackTrace());
				throw cx;
			} else if (x instanceof SocketTimeoutException) {
				int timeout = httpRequest.getParams().getIntParameter(HttpMethodParams.SO_TIMEOUT,
						config.getReadTimeoutMillis());
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + timeout + " ms");
				stx.setStackTrace(x.getStackTrace());
				throw stx;
			} else if (x instanceof ConnectException) {
				//enhance message with url
				ConnectException ctx = new ConnectException("Connection refused " + config.getHostUrl());
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof IOException) {
				throw (IOException) x;//just rethrow IO
			} else {
				throw new IOException(x.getMessage(), x);//wrap others
			}
		}
	}

	private static class ObjectEntity implements RequestEntity {

		private final Object objectBody;

		private final Charset charset;

		private byte[] content;

		private final RequestBodyMarshaller marshaller;

		private ObjectEntity(Object objectBody, Charset charset, RequestBodyMarshaller marshaller, boolean streaming)
				throws IOException {
			this.objectBody = objectBody;
			this.marshaller = marshaller;
			this.charset = charset;
			if (streaming) {
				this.content = null;
			} else {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				marshaller.write(objectBody, baos, charset);
				this.content = baos.toByteArray();
			}
		}

		@Override
		public boolean isRepeatable() {
			return true;
		}

		@Override
		public void writeRequest(OutputStream outstream) throws IOException {
			if (this.content != null) {
				outstream.write(content, 0, content.length);
				outstream.flush();
			} else {
				//streaming
				marshaller.write(objectBody, outstream, charset);
			}

		}

		@Override
		public long getContentLength() {
			return this.content != null ? content.length : -1;
		}

		@Override
		public String getContentType() {
			return null; //we set ContentType before
		}

	}
}
