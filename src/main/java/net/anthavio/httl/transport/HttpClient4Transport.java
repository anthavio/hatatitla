package net.anthavio.httl.transport;

import java.io.ByteArrayInputStream;
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
import net.anthavio.httl.HttlMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.util.ReaderInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreConnectionPNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Transport implements HttlTransport {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpClient httpClient;

	private final HttpClient4Config config;

	public HttpClient4Transport(HttpClient4Config config) {
		this.config = config;
		this.httpClient = config.newHttpClient();
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
	public HttpClient4Response call(HttlRequest request) throws IOException {

		String urlFile = request.getPathAndQuery();
		HttpRequestBase httpRequest;
		switch (request.getMethod()) {
		case GET:
			httpRequest = new HttpGet(urlFile);
			break;
		case HEAD:
			httpRequest = new HttpHead(urlFile);
			break;
		case OPTIONS:
			httpRequest = new HttpOptions(urlFile);
			break;
		case TRACE:
			httpRequest = new HttpTrace(urlFile);
			break;
		case DELETE:
			httpRequest = new HttpDelete(urlFile);
			break;
		case POST:
			HttpPost post = new HttpPost(urlFile);
			setEntity(request, post);
			httpRequest = post;
			break;
		case PUT:
			HttpPut put = new HttpPut(urlFile);
			setEntity(request, put);
			httpRequest = put;
			break;
		case PATCH:
			HttpPatch patch = new HttpPatch(urlFile);
			setEntity(request, patch);
			httpRequest = patch;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		HttpHeaders headers = request.getHeaders();
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

		HttpResponse httpResponse = call(httpRequest);

		Header[] responseHeaders = httpResponse.getAllHeaders();
		HttpHeaders outHeaders = new HttpHeaders();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}

		StatusLine statusLine = httpResponse.getStatusLine();

		HttpEntity entity = httpResponse.getEntity();
		//Entity is null for http 300 redirects
		InputStream responseStream = entity != null ? entity.getContent() : null;
		HttpClient4Response response = new HttpClient4Response(request, statusLine.getStatusCode(),
				statusLine.getReasonPhrase(), outHeaders, responseStream, httpResponse);
		return response;
	}

	protected void setEntity(HttlRequest request, HttpEntityEnclosingRequestBase into) throws IOException {
		HttlBody body = request.getBody();
		if (body != null) {
			HttpEntity entity;
			switch (body.getType()) {
			case MARSHALL:
				entity = new ObjectHttpEntity(body.getPayload(), Charset.forName(request.getCharset()), body.getMarshaller());
				break;
			case STRING:
				entity = new StringEntity((String) body.getPayload(), request.getCharset());
				break;
			case BYTES:
				entity = new ByteArrayEntity((byte[]) body.getPayload());
				break;
			case STREAM:
				entity = new InputStreamEntity((InputStream) body.getPayload(), -1);
				break;
			case READER:
				entity = new InputStreamEntity(new ReaderInputStream((Reader) body.getPayload()), -1);
				break;
			default:
				throw new IllegalStateException("Unsupported HttlBody type: " + body.getType());
			}
			into.setEntity(entity);
		}
	}

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
				ConnectException ctx = new ConnectException("Connection refused " + config.getUrl());
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

		private byte[] content;

		private final HttlMarshaller marshaller;

		private ObjectHttpEntity(Object objectBody, Charset charset, HttlMarshaller marshaller) throws IOException {
			this.objectBody = objectBody;
			this.marshaller = marshaller;
			this.charset = charset;

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			marshaller.write(objectBody, baos, charset);
			this.content = baos.toByteArray();
		}

		@Override
		public boolean isRepeatable() {
			return this.content != null;
		}

		@Override
		public long getContentLength() {
			return this.content != null ? content.length : -1;
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			return new ByteArrayInputStream(content);
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
			return false;
		}

	}

}
