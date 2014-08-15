package net.anthavio.httl;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.anthavio.httl.HttlParameterSetter.ConfigurableParamSetter;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.marshall.HttlBytesExtractor;
import net.anthavio.httl.marshall.HttlStringExtractor;
import net.anthavio.httl.marshall.MediaTypeMarshaller;
import net.anthavio.httl.marshall.MediaTypeUnmarshaller;
import net.anthavio.httl.util.Cutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderBuilder {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final URL url;

	private Authentication authentication;

	private String encoding = "utf-8";

	private Charset charset = Charset.forName(this.encoding);

	private int poolMaximumSize = 10; //maximal number of pooled connections

	private int connectTimeoutMillis = 5 * 1000; //in millis

	private int readTimeoutMillis = 10 * 1000; //in millis

	private boolean followRedirects = false;

	private ExecutorService executorService;

	private final Multival<String> defaultHeaders = new Multival<String>();

	private final Multival<String> defaultParameters = new Multival<String>();

	private HttlParameterSetter paramSetter = new ConfigurableParamSetter();

	private final List<HttlExecutionFilter> executionFilters = new ArrayList<HttlExecutionFilter>();

	private final List<HttlBuilderVisitor> builderVisitors = new ArrayList<HttlBuilderVisitor>();

	private HttlBodyMarshaller marshaller = new MediaTypeMarshaller();

	private HttlBodyUnmarshaller unmarshaller = new MediaTypeUnmarshaller();

	private HttlResponseExtractor<String> stringExtractor = new HttlStringExtractor(200, 299);

	private HttlResponseExtractor<byte[]> bytesExtractor = new HttlBytesExtractor(200, 299);

	public SenderBuilder(URL url) {
		this.url = digHostUrl(url);
	}

	public SenderBuilder(String urlString) {
		if (Cutils.isBlank(urlString)) {
			throw new IllegalArgumentException("URL is blank");
		}
		if (urlString.startsWith("http") == false) {
			urlString = "http://" + urlString;
		}
		//try to parse
		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException("URL is invalid " + urlString, mux);
		}
		if (Cutils.isBlank(url.getHost())) {
			throw new IllegalArgumentException("URL has no host " + urlString);
		}
		this.url = digHostUrl(url);
	}

	private URL digHostUrl(URL url) {
		if (!Cutils.isEmpty(url.getQuery())) {
			logger.info("Query part is discarded from url " + url);
		}

		try {
			url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath());
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException(mux);
		}
		return url;
	}

	/**
	 * Builder's build method right?
	 */
	public abstract HttlSender build();

	public abstract HttlTransport getTransport();

	public URL getUrl() {
		return this.url;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public SenderBuilder setEncoding(String encoding) {
		this.encoding = encoding;
		this.charset = Charset.forName(encoding);
		//Update Content-Type header is already exists
		String contentType = this.defaultHeaders.getFirst(HttlConstants.Content_Type);
		if (contentType != null) {
			this.defaultHeaders.set(HttlConstants.Content_Type, contentType.substring(0, contentType.indexOf("; charset="))
					+ "; charset=" + encoding);
		}
		return this;
	}

	public Charset getCharset() {
		return this.charset;
	}

	/**
	 * @return Timeout for creating connection in millis (CONNECTION_TIMEOUT)
	 */
	public int getConnectTimeoutMillis() {
		return this.connectTimeoutMillis;
	}

	/**
	 * Timeout for creating connection in millis (CONNECTION_TIMEOUT)
	 */
	public SenderBuilder setConnectTimeoutMillis(int millis) {
		this.connectTimeoutMillis = millis;
		return this;
	}

	/**
	 * @return Timeout for reading response in millis (SO_TIMEOUT)
	 */
	public int getReadTimeoutMillis() {
		return this.readTimeoutMillis;
	}

	/**
	 * Timeout for reading response in millis (SO_TIMEOUT)
	 */
	public SenderBuilder setReadTimeoutMillis(int millis) {
		this.readTimeoutMillis = millis;
		return this;
	}

	/**
	 * @return Maximal number of pooled connections
	 */
	public int getPoolMaximumSize() {
		return poolMaximumSize;
	}

	/**
	 * Maximal number of pooled connections
	 */
	public SenderBuilder setPoolMaximumSize(int poolMaximum) {
		this.poolMaximumSize = poolMaximum;
		return this;
	}

	public Authentication getAuthentication() {
		return this.authentication;
	}

	public SenderBuilder setAuthentication(Authentication authentication) {
		this.authentication = authentication;
		return this;
	}

	public boolean getFollowRedirects() {
		return followRedirects;
	}

	public SenderBuilder setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
		return this;
	}

	/**
	 * Header will be added into every HttlRequest
	 */
	public SenderBuilder setHeader(String name, String value) {
		this.defaultHeaders.set(name, value);
		return this;
	}

	/**
	 * @return Header added into every HttlRequest
	 */
	public Multival<String> getDefaultHeaders() {
		return defaultHeaders;
	}

	/**
	 * Parameter will be added into every HttlRequest
	 */
	public SenderBuilder setParam(String name, String value) {
		this.defaultParameters.set(name, value);
		return this;
	}

	/**
	 * @return Parameters added into every HttlRequest
	 */
	public Multival<String> getDefaultParameters() {
		return defaultParameters;
	}

	public HttlBodyUnmarshaller getUnmarshaller() {
		return unmarshaller;
	}

	public SenderBuilder setUnmarshaller(HttlBodyUnmarshaller unmarshaller) {
		this.unmarshaller = unmarshaller;
		return this;
	}

	public HttlBodyMarshaller getMarshaller() {
		return marshaller;
	}

	public SenderBuilder setMarshaller(HttlBodyMarshaller marshaller) {
		this.marshaller = marshaller;
		return this;
	}

	public HttlResponseExtractor<String> getStringExtractor() {
		return stringExtractor;
	}

	public void setStringExtractor(HttlResponseExtractor<String> extractor) {
		if (extractor == null) {
			throw new IllegalArgumentException("Null extractor");
		}
		this.stringExtractor = extractor;
	}

	public HttlResponseExtractor<byte[]> getBytesExtractor() {
		return bytesExtractor;
	}

	public void setBytesExtractor(HttlResponseExtractor<byte[]> extractor) {
		if (extractor == null) {
			throw new IllegalArgumentException("Null extractor");
		}
		this.bytesExtractor = extractor;
	}

	public SenderBuilder addExecutionFilter(HttlExecutionFilter filter) {
		if (filter == null) {
			throw new IllegalArgumentException("Null filter");
		}
		executionFilters.add(filter);
		return this;
	}

	public List<HttlExecutionFilter> getExecutionFilters() {
		return executionFilters;
	}

	public SenderBuilder addBuilderVisitor(HttlBuilderVisitor visitor) {
		if (visitor == null) {
			throw new IllegalArgumentException("Null filter");
		}
		builderVisitors.add(visitor);
		return this;
	}

	public List<HttlBuilderVisitor> getBuilderVisitors() {
		return builderVisitors;
	}

	/**
	 * 'Accept' header set into every Request
	 */
	public void setResponseMediaType(String mediaType) {
		this.defaultHeaders.set(HttlConstants.Accept, mediaType);
	}

	/**
	 * 'Content-Type' header set into every Request
	 */
	public void setRequestMediaType(String mediaType) {
		if (mediaType != null && mediaType.indexOf("charset") != -1) {
			throw new IllegalArgumentException("Use media type without charset: " + mediaType);
		}
		this.defaultHeaders.set(HttlConstants.Content_Type, mediaType + "; charset=" + charset.name());
	}

	public HttlParameterSetter getParamSetter() {
		return paramSetter;
	}

	public SenderBuilder setParamSetter(HttlParameterSetter paramSetter) {
		if (paramSetter == null) {
			throw new IllegalArgumentException("Null setter");
		}
		this.paramSetter = paramSetter;
		return this;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public SenderBuilder setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
		return this;
	}

}
