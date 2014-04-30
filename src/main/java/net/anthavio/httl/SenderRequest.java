package net.anthavio.httl;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public class SenderRequest {

	public static enum ValueStrategy {
		SKIP, KEEP
	}

	public static enum EncodeStrategy {
		ENCODE, DONOT
	}

	public static enum Method {
		GET(false), DELETE(false), HEAD(false), PATCH(false), OPTIONS(false), //
		POST(true), PUT(true);

		private boolean canHaveBody;

		private Method(boolean canHaveBody) {
			this.canHaveBody = canHaveBody;
		}

		public boolean canHaveBody() {
			return this.canHaveBody;
		}
	}

	protected static final String DEFAULT_URI = "/";

	protected transient HttpSender sender;

	private final Method method;

	private final String urlPath;

	private Multival headers;

	private Multival parameters;

	private Integer readTimeoutMillis; //millis - override config value

	private ValueStrategy nullValueStrategy = null;

	private ValueStrategy emptyValueStrategy = null;

	private EncodeStrategy urlEncodingStrategy = null;

	// Constructors of managed request instance knowing it's Sender

	protected SenderRequest(HttpSender sender, Method method, String urlPath) {
		this(sender, method, urlPath, null, null);
	}

	public SenderRequest(HttpSender sender, Method method, String urlPath, Multival parameters, Multival headers) {
		this.sender = sender; //can be null

		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}
		this.method = method;

		if (Cutils.isEmpty(urlPath)) {
			throw new IllegalArgumentException("Empty url path");
		}

		if (urlPath != null) {
			this.urlPath = urlPath;
		} else {
			this.urlPath = DEFAULT_URI;
		}

		if (parameters != null) {
			this.parameters = parameters;
		} else {
			this.parameters = new Multival();
		}

		if (headers != null) {
			this.headers = headers;
		} else {
			this.headers = new Multival();
		}
	}

	// Constructors of standalone request instance without reference to it's Sender
	/*
	public SenderRequest(Method method) {
		this(null, method, null, null, null);
	}

	public SenderRequest(Method method, Multival parameters) {
		this(null, method, null, parameters, null);
	}
	*/
	public SenderRequest(Method method, String urlPath) {
		this(null, method, urlPath, null, null);
	}

	public SenderRequest(Method method, String urlPath, Multival parameters) {
		this(null, method, urlPath, parameters, null);
	}

	/**
	 * Way to attach Request to Sender if it was not done through constructor
	 */
	public void setSender(HttpSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("sender is null");
		}
		this.sender = sender;
	}

	public HttpSender getSender() {
		return sender;
	}

	/**
	 * POST or PUT requests should override this
	 */
	public boolean hasBody() {
		return false;
	}

	public Method getMethod() {
		return this.method;
	}

	public String getUrlPath() {
		return this.urlPath;
	}

	public Integer getReadTimeoutMillis() {
		return readTimeoutMillis;
	}

	public void setReadTimeoutMillis(int readTimeoutMillis) {
		this.readTimeoutMillis = readTimeoutMillis;
	}

	public void setReadTimeout(int readTimeout, TimeUnit unit) {
		this.readTimeoutMillis = (int) unit.toMillis(readTimeout);
	}

	public ValueStrategy getNullValueStrategy() {
		return nullValueStrategy;
	}

	public void setNullValueStrategy(ValueStrategy nullValueStrategy) {
		this.nullValueStrategy = nullValueStrategy;
	}

	public ValueStrategy getEmptyValueStrategy() {
		return emptyValueStrategy;
	}

	public void setEmptyValueStrategy(ValueStrategy emptyValueStrategy) {
		this.emptyValueStrategy = emptyValueStrategy;
	}

	public EncodeStrategy getUrlEncodingStrategy() {
		return urlEncodingStrategy;
	}

	public void setUrlEncodingStrategy(EncodeStrategy urlEncodingStrategy) {
		this.urlEncodingStrategy = urlEncodingStrategy;
	}

	public Multival getHeaders() {
		return this.headers;
	}

	/**
	 * Set Headers replacing all existing
	 */
	public SenderRequest setHeaders(Map<String, ?> headers) {
		this.headers = new Multival(headers);
		return this;
	}

	/**
	 * Add Headers
	 */
	public SenderRequest addHeaders(Map<String, ?> headers) {
		Set<String> keySet = headers.keySet();
		for (String key : keySet) {
			this.headers.add(key, headers.get(key));
		}
		return this;
	}

	/**
	 * Set Header with single value
	 * If Header already exists, old value(s) will be replaced
	 */
	public SenderRequest setHeader(String name, Serializable value) {
		this.headers.put(name, value, true);
		return this;
	}

	/**
	 * Set Header with multiple values
	 * If Header already exists, old value(s) will be replaced
	 */
	public <T> SenderRequest setHeader(String name, T... values) {
		this.headers.put(name, values, true);
		return this;
	}

	/**
	 * Add Header with single value
	 * If header already exists, new value will be added to it
	 */
	public SenderRequest addHeader(String name, Object value) {
		this.headers.put(name, value, false);
		return this;
	}

	/**
	 * Add Header with multiple values
	 * If header already exists, new values will be added to it
	 */
	public <T> SenderRequest addHeader(String name, T... values) {
		this.headers.put(name, values, false);
		return this;
	}

	/**
	 * @return first value of Header
	 */
	public String getFirstHeader(String name) {
		return headers.getFirst(name);
	}

	public List<String> getHeader(String name) {
		return headers.get(name);
	}

	//parameters section

	public Multival getParameters() {
		return this.parameters;
	}

	/**
	 * Set Parameters replacing all existing
	 */
	public SenderRequest setParameters(Map<String, ?> parameters) {
		this.parameters = new Multival(parameters);
		return this;
	}

	/**
	 * Set Parameters replacing all existing
	 */
	public SenderRequest setParameters(Multival parameters) {
		this.parameters = parameters;
		return this;
	}

	/**
	 * Add Parameters
	 */
	public SenderRequest addParameters(Map<String, ?> headers) {
		Set<String> keySet = headers.keySet();
		for (String key : keySet) {
			this.parameters.add(key, headers.get(key));
		}
		return this;
	}

	/**
	 * Add parameter with single value.
	 * If parameter already exists, new value will be added to it.
	 */
	public SenderRequest addParameter(String name, Object value) {
		this.parameters.put(name, value, false);
		return this;
	}

	/**
	 * Add parameter with multiple values.
	 * If parameter already exists, new values will be added to it.
	 */
	public <T> SenderRequest addParameter(String name, T... values) {
		this.parameters.put(name, values, false);
		return this;
	}

	/**
	 * Add parameter without value.
	 * 
	 * BewaRe nullValueStrategy setting!
	 */
	public <T> SenderRequest addParameter(String name) {
		this.parameters.put(name, null, false);
		return this;
	}

	/**
	 * Set parameter with single value.
	 * If Header already exists, old value(s) will be replaced.
	 */
	public SenderRequest setParameter(String name, Object value) {
		this.parameters.put(name, value, true);
		return this;
	}

	/**
	 * Set parameter with multiple values.
	 * If Header already exists, old value(s) will be replaced.
	 */
	public <T> SenderRequest setParameter(String name, T... values) {
		this.parameters.put(name, values, true);
		return this;
	}

	/**
	 * Matrix parameter is allways part of URL.
	 */
	public SenderRequest addMartixParam(String name, Object value) {
		if (name.charAt(0) != ';') {
			name = ";" + name;
		}
		this.parameters.put(name, value, false);
		return this;
	}

	/**
	 * Matrix parameter is allways part of URL.
	 */
	public SenderRequest setMartixParam(String name, Object value) {
		if (name.charAt(0) != ';') {
			name = ";" + name;
		}
		this.parameters.put(name, value, true);
		return this;
	}

	/**
	 * URL path parameter is allways part of ...well... URL.
	 */
	public SenderRequest setUrlParam(String name, Object value) {
		if (name.charAt(0) != '{') {
			name = "{" + name + "}";
		}
		if (urlPath.indexOf(name) == -1) {
			throw new IllegalArgumentException("URL path variable: " + name + " not found in: " + urlPath);
		}
		this.parameters.put(name, value, true);
		return this;
	}

	//XXX test this....
	public URI getUri() {
		if (sender == null) {
			return URI.create(urlPath);
		} else {
			URL url = sender.getConfig().getHostUrl();
			String[] pathAndQuery = sender.getPathAndQuery(this); //XXX NOT all params are query for POST
			String path = pathAndQuery[0];
			String query = pathAndQuery[1];
			try {
				return new URI(url.getProtocol(), null, url.getHost(), url.getPort(), path, query, null);
			} catch (URISyntaxException usx) {
				IllegalArgumentException iax = new IllegalArgumentException();
				iax.initCause(usx);
				throw iax;
			}
			//return new URL(url.getProtocol(), url.getHost(), url.getPort(), path);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.method == null ? 0 : this.method.hashCode());
		result = prime * result + (this.urlPath == null ? 0 : this.urlPath.hashCode());
		result = prime * result + (this.parameters == null ? 0 : this.parameters.hashCode());
		result = prime * result + (this.headers == null ? 0 : this.headers.hashCode());
		//body is stream and have only Object hashcode
		//result = prime * result + (this.bodyStream == null ? 0 : this.bodyStream.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SenderRequest other = (SenderRequest) obj;
		if (this.method != other.method) {
			return false;
		}
		if (this.urlPath == null) {
			if (other.urlPath != null) {
				return false;
			}
		} else if (!this.urlPath.equals(other.urlPath)) {
			return false;
		}
		if (this.parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!this.parameters.equals(other.parameters)) {
			return false;
		}
		if (this.headers == null) {
			if (other.headers != null) {
				return false;
			}
		} else if (!this.headers.equals(other.headers)) {
			return false;
		}
		//body is stream and have only Object equals
		/*
		if (this.bodyStream == null) {
			if (other.bodyStream != null) {
				return false;
			}
		} else if (!this.bodyStream.equals(other.bodyStream)) {
			return false;
		}
		*/
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(method);
		sb.append(' ');
		if (sender != null) {
			sb.append(sender.getConfig().getHostUrl() + urlPath);
		} else {
			sb.append(urlPath);
		}
		sb.append(' ');
		sb.append(parameters);
		return sb.toString();
	}

}
