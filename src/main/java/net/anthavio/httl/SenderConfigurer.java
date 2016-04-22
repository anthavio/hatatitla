package net.anthavio.httl;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.anthavio.httl.HttlParameterSetter.ConfigurableParamSetter;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.api.HttlApiBuilder;
import net.anthavio.httl.marshall.HttlBytesExtractor;
import net.anthavio.httl.marshall.HttlStringExtractor;
import net.anthavio.httl.marshall.MediaTypeMarshaller;
import net.anthavio.httl.marshall.MediaTypeUnmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class SenderConfigurer {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

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

	private final HttlTransport transport;

	private final URL url;

	private final String charset;

	private boolean skipCharset = false;

	public SenderConfigurer(HttlTransport transport) {
		if (transport == null) {
			throw new IllegalArgumentException("Null transport");
		}
		this.transport = transport;
		//copy commonly used values
		this.url = transport.getConfig().getTarget().getUrl();
		this.charset = transport.getConfig().getCharset();
	}

	/**
	 * Finalize HttlSender creation and continute with API builder
	 */
	public HttlApiBuilder api() {
		HttlSender sender = build();
		return new HttlApiBuilder(sender);
	}

	/**
	 * Build final HttlSender
	 */
	public HttlSender build() {
		return new HttlSender(this);
	}

	public HttlTransport getTransport() {
		return transport;
	}

	public URL getUrl() {
		return url;
	}

	public String getCharset() {
		return charset;
	}

	/**
	 * Header will be added into every HttlRequest
	 */
	public SenderConfigurer addHeader(String name, String value) {
		this.defaultHeaders.add(name, value);
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
	public SenderConfigurer addParam(String name, String value) {
		this.defaultParameters.add(name, value);
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

	public SenderConfigurer setUnmarshaller(HttlBodyUnmarshaller unmarshaller) {
		this.unmarshaller = unmarshaller;
		return this;
	}

	public HttlBodyMarshaller getMarshaller() {
		return marshaller;
	}

	public SenderConfigurer setMarshaller(HttlBodyMarshaller marshaller) {
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

	public SenderConfigurer addExecutionFilter(HttlExecutionFilter filter) {
		if (filter == null) {
			throw new IllegalArgumentException("Null filter");
		}
		executionFilters.add(filter);
		return this;
	}

	public List<HttlExecutionFilter> getExecutionFilters() {
		return executionFilters;
	}

	public SenderConfigurer addBuilderVisitor(HttlBuilderVisitor visitor) {
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
	public SenderConfigurer setResponseMediaType(String mediaType) {
		this.defaultHeaders.set(HttlConstants.Accept, mediaType);
		return this;
	}

	/**
	 * 'Content-Type' header set into every Request
	 */
	public SenderConfigurer setRequestMediaType(String mediaType) {
		if (mediaType != null && mediaType.indexOf("charset") != -1) {
			throw new IllegalArgumentException("Use media type without charset: " + mediaType);
		}
		this.defaultHeaders.set(HttlConstants.Content_Type, mediaType + "; charset=" + transport.getConfig().getCharset());
		return this;
	}

	public HttlParameterSetter getParamSetter() {
		return paramSetter;
	}

	public SenderConfigurer setParamSetter(HttlParameterSetter paramSetter) {
		if (paramSetter == null) {
			throw new IllegalArgumentException("Null setter");
		}
		this.paramSetter = paramSetter;
		return this;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public SenderConfigurer setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
		return this;
	}

	public boolean isSkipCharset() {
		return skipCharset;
	}

	/**
	 * When true, 'Content-Type' header 'charset' fragment will be skipped (not set)
	 */
	public SenderConfigurer setSkipCharset(boolean omitCharset) {
		this.skipCharset = omitCharset;
		return this;
	}

}
