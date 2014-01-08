package net.anthavio.httl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.anthavio.httl.SenderBodyRequest.FakeStream;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreConnectionPNames;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Sender extends HttpSender {

	private final HttpClient httpClient;

	private final HttpClient4Config config;

	public HttpClient4Sender(String baseUrl) {
		this(new HttpClient4Config(baseUrl));
	}

	public HttpClient4Sender(HttpClient4Config config) {
		super(config);
		this.config = config;
		this.httpClient = config.buildHttpClient();
	}

	public HttpClient4Sender(String baseUrl, ExecutorService executor) {
		this(new HttpClient4Config(baseUrl), executor);
	}

	public HttpClient4Sender(HttpClient4Config config, ExecutorService executor) {
		super(config, executor);
		this.config = config;
		this.httpClient = config.buildHttpClient();
	}

	@Override
	public HttpClient4Config getConfig() {
		return config;
	}

	@Override
	public void close() {
		try {
			httpClient.getConnectionManager().shutdown();
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}

	/*
	public void reset() {
		try {
			httpClient.getConnectionManager().closeExpiredConnections();
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}
	*/

	/**
	 * Backdoor
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}

	@Override
	protected HttpClient4Response doExecute(SenderRequest request, String path, String query) throws IOException {

		HttpRequestBase httpRequest;
		switch (request.getMethod()) {
		case GET:
			httpRequest = new HttpGet(path);
			break;
		case DELETE:
			httpRequest = new HttpDelete(path);
			break;
		case HEAD:
			httpRequest = new HttpHead(path);
			break;
		case OPTIONS:
			httpRequest = new HttpOptions(path);
			break;
		case POST:
			HttpPost httpPost = new HttpPost(path);
			HttpEntity entityPost = buildEntity(request, query);
			httpPost.setEntity(entityPost);
			httpRequest = httpPost;
			break;
		case PUT:
			HttpPut httpPut = new HttpPut(path);
			HttpEntity entityPut = buildEntity(request, query);
			httpPut.setEntity(entityPut);
			httpRequest = httpPut;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		Multival headers = request.getHeaders();
		if (headers != null && headers.size() != 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					httpRequest.addHeader(name, value);
				}
			}
		}

		if (request.getReadTimeoutMillis() != null) {
			httpRequest.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, request.getReadTimeoutMillis());
		}

		if (config.getGzipRequest()) {
			httpRequest.addHeader("Accept-Encoding", "gzip, deflate");
			//httpRequest.addHeader("Content-Encoding", "gzip");
		}

		if (request.hasBody()) {
			String contentType = request.getFirstHeader("Content-Type");
			if (contentType == null) {
				throw new IllegalArgumentException("Request with body must have Content-Type header specified");
			}
			//add charset into Content-Type header if missing
			int idxCharset = contentType.indexOf("charset=");
			if (idxCharset == -1) {
				contentType = contentType + "; charset=" + config.getCharset();
				httpRequest.setHeader("Content-Type", contentType);
			}
		}

		if (request.getFirstHeader("Accept") == null && config.getDefaultAccept() != null) {
			httpRequest.addHeader("Accept", config.getDefaultAccept());
		}

		if (request.getFirstHeader("Accept-Charset") == null) {
			httpRequest.addHeader("Accept-Charset", config.getEncoding());
		}

		HttpResponse httpResponse = call(httpRequest);

		Header[] responseHeaders = httpResponse.getAllHeaders();
		Multival outHeaders = new Multival();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}

		StatusLine statusLine = httpResponse.getStatusLine();

		HttpEntity entity = httpResponse.getEntity();
		//Entity is null for http 300 redirects
		InputStream responseStream = entity != null ? entity.getContent() : null;
		HttpClient4Response response = new HttpClient4Response(statusLine.getStatusCode(), statusLine.getReasonPhrase(),
				outHeaders, responseStream, httpResponse);
		return response;
	}

	private HttpEntity buildEntity(SenderRequest request, String query) throws IOException {
		String contentType = request.getFirstHeader("Content-Type");
		Object[] type = HttpHeaderUtil.splitContentType(contentType, config.getCharset());
		String mimeType = (String) type[0];
		Charset charset = (Charset) type[1];

		HttpEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((SenderBodyRequest) request).getBodyStream();
			if (stream instanceof FakeStream) {
				FakeStream fake = (FakeStream) stream;
				if (fake.getValue() instanceof String) {
					entity = new StringEntity((String) fake.getValue(), charset);
				} else {
					RequestBodyMarshaller marshaller = getRequestMarshaller(mimeType);
					if (marshaller == null) {
						throw new IllegalArgumentException("Request body marshaller not found for " + mimeType);
					}
					entity = new ObjectHttpEntity(fake.getValue(), charset, marshaller, fake.isStreaming());
				}
			} else { //plain InputStream
				entity = new InputStreamEntity(stream, -1);
			}
		} else if (query != null && query.length() != 0) {
			entity = new StringEntity(query, ContentType.create(URLEncodedUtils.CONTENT_TYPE, charset));
		} else {
			logger.debug("Body request does not have any parameters or body");
			entity = new StringEntity("", ContentType.create(URLEncodedUtils.CONTENT_TYPE, charset));
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	/*
		private HttpEntity buildEntity(SenderRequest request, List<NameValuePair> nvQuParams)
				throws UnsupportedEncodingException {
			HttpEntity entity;
			if (request.hasBody()) {
				InputStream stream = ((BodyRequest) request).getBodyStream();
				if (stream instanceof StringWrappingStream) {
					entity = new StringEntity(((StringWrappingStream) stream).getString(), config.getCharset());
				} else {
					entity = new InputStreamEntity(stream, -1);
				}
			} else if (nvQuParams != null && nvQuParams.size() != 0) {
				entity = new UrlEncodedFormEntity(nvQuParams, config.getCharset());
			} else {
				logger.debug("POST request does not have any parameters or body");
				entity = new StringEntity("");
				//throw new IllegalArgumentException("POST request does not have any parameters or body");
			}
			return entity;
		}
	*/
	protected HttpResponse call(HttpRequestBase httpRequest) throws IOException {
		try {
			if (config.getAuthContext() != null) {
				return this.httpClient.execute(httpRequest, config.getAuthContext());
			} else {
				return this.httpClient.execute(httpRequest);
			}
		} catch (Exception x) {
			//connection might be already open so release it
			httpRequest.releaseConnection();
			if (x instanceof ConnectionPoolTimeoutException) {
				ConnectException ctx = new ConnectException("Pool timeout " + config.getPoolAcquireTimeoutMillis() + " ms");
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof ConnectTimeoutException) {
				ConnectException ctx = new ConnectException("Connect timeout " + config.getConnectTimeoutMillis() + " ms");
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof HttpHostConnectException) {
				//connection refused
				ConnectException ctx = new ConnectException("Connection refused " + config.getHostUrl());
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof SocketTimeoutException) {
				int timeout = httpRequest.getParams().getIntParameter(CoreConnectionPNames.SO_TIMEOUT,
						config.getReadTimeoutMillis());
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + timeout + " ms");
				stx.setStackTrace(x.getStackTrace());
				throw stx;
				//java.net.ConnectException: Connection refused
			} else if (x instanceof IOException) {
				throw (IOException) x;//just rethrow IO
			} else {
				throw new IOException(x);//wrap others
			}
		}
	}

	private static class ObjectHttpEntity extends AbstractHttpEntity {

		private final Object objectBody;

		private final Charset charset;

		private final boolean streaming;

		private byte[] content;

		private final RequestBodyMarshaller marshaller;

		private ObjectHttpEntity(Object objectBody, Charset charset, RequestBodyMarshaller marshaller, boolean streaming)
				throws IOException {
			this.objectBody = objectBody;
			this.marshaller = marshaller;
			this.charset = charset;
			this.streaming = streaming;
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
		public long getContentLength() {
			return this.content != null ? content.length : -1;
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			return null;
		}

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			if (this.content != null) {
				outstream.write(content, 0, content.length);
				outstream.flush();
			} else {
				//streaming
				marshaller.write(objectBody, outstream, charset);
			}
		}

		@Override
		public boolean isStreaming() {
			return streaming;
		}

	}

}
