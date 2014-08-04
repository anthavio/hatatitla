package net.anthavio.httl.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;

import net.anthavio.httl.HttlBody;
import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.ReaderInputStream;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.ConnectionPoolTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Transport implements HttlTransport {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpClient httpClient;

	private final HttpClient3Config config;

	public HttpClient3Transport(HttpClient3Config config) {
		this.config = config;
		this.httpClient = config.newHttpClient();
	}

	@Override
	public void close() {
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
	public HttpClient3Response call(HttlRequest request) throws IOException {

		String urlFile = request.getPathAndQuery();
		HttpMethodBase httpMethod;
		switch (request.getMethod()) {
		case GET:
			httpMethod = new GetMethod(urlFile);
			break;
		case HEAD:
			httpMethod = new HeadMethod(urlFile);
			break;
		case OPTIONS:
			httpMethod = new OptionsMethod(urlFile);
			break;
		case DELETE:
			httpMethod = new DeleteMethod(urlFile);
			break;
		case POST:
			PostMethod httpPost = new PostMethod(urlFile);
			setEntity(request, httpPost);
			httpMethod = httpPost;
			break;
		case PUT:
			PutMethod httpPut = new PutMethod(urlFile);
			setEntity(request, httpPut);
			httpMethod = httpPut;
			break;
		case PATCH:
			PatchMethod httpPatch = new PatchMethod(urlFile);
			setEntity(request, httpPatch);
			httpMethod = httpPatch;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		HttpHeaders headers = request.getHeaders();
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

		int statusCode = call(httpMethod);

		Header[] responseHeaders = httpMethod.getResponseHeaders();

		HttpHeaders outHeaders = new HttpHeaders();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}
		StatusLine statusLine = httpMethod.getStatusLine();

		InputStream responseStream = httpMethod.getResponseBodyAsStream();

		HttpClient3Response response = new HttpClient3Response(request, statusCode, statusLine.getReasonPhrase(),
				outHeaders, responseStream, httpMethod);
		return response;
	}

	private void setEntity(HttlRequest request, EntityEnclosingMethod into) throws IOException {
		HttlBody body = request.getBody();
		if (body != null) {
			RequestEntity entity;
			switch (body.getType()) {
			case MARSHALL:
				entity = new ObjectEntity(body.getPayload(), Charset.forName(request.getCharset()), body.getMarshaller());
				break;
			case STRING:
				entity = new StringRequestEntity((String) body.getPayload(), null, request.getCharset());
				break;
			case BYTES:
				entity = new ByteArrayRequestEntity((byte[]) body.getPayload());
				break;
			case STREAM:
				entity = new InputStreamRequestEntity((InputStream) body.getPayload());
				break;
			case READER:
				entity = new InputStreamRequestEntity(new ReaderInputStream((Reader) body.getPayload()));
				break;
			default:
				throw new IllegalStateException("Unsupported HttlBody type: " + body.getType());
			}
			into.setRequestEntity(entity);
		}
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
				ConnectException ctx = new ConnectException("Connection refused " + config.getUrl());
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

		private final HttlBodyMarshaller marshaller;

		private ObjectEntity(Object objectBody, Charset charset, HttlBodyMarshaller marshaller) throws IOException {
			this.objectBody = objectBody;
			this.marshaller = marshaller;
			this.charset = charset;

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			marshaller.write(objectBody, baos, charset);
			this.content = baos.toByteArray();
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

class PatchMethod extends EntityEnclosingMethod {

	public PatchMethod() {
		super();
	}

	public PatchMethod(String uri) {
		super(uri);
	}

	public String getName() {
		return "PATCH";
	}
}
