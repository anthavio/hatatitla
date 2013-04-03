package com.anthavio.hatatitla;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.anthavio.hatatitla.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderRequest {

	public static enum Method {
		GET(false), DELETE(false), HEAD(false), OPTIONS(false), POST(true), PUT(true);

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

	// Constructors of managed request instance knowing it's Sender

	protected SenderRequest(HttpSender sender, Method method, String urlPath) {
		this(sender, method, urlPath, null, null);
	}

	protected SenderRequest(HttpSender sender, Method method, String urlPath, Multival parameters, Multival headers) {
		this.sender = sender; //can be null

		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}
		this.method = method;

		if (Cutils.isEmpty(urlPath)) {
			throw new IllegalArgumentException();
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

	public SenderRequest(Method method) {
		this(null, method, null, null, null);
	}

	public SenderRequest(Method method, Multival parameters) {
		this(null, method, null, parameters, null);
	}

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

	public Integer getReadTimeout() {
		return readTimeoutMillis;
	}

	public void setReadTimeoutMillis(int readTimeoutMillis) {
		this.readTimeoutMillis = readTimeoutMillis;
	}

	public void setReadTimeout(int readTimeout, TimeUnit unit) {
		this.readTimeoutMillis = (int) unit.toMillis(readTimeout);
	}

	public Multival getHeaders() {
		return this.headers;
	}

	public SenderRequest setHeaders(Multival headers) {
		this.headers = headers;
		return this;
	}

	/**
	 * Add Headers from java.util.Map
	 */
	public SenderRequest addHeaders(Map<String, String> headers) {
		Set<Entry<String, String>> entrySet = headers.entrySet();
		for (Entry<String, String> entry : entrySet) {
			this.headers.add(entry.getKey(), entry.getValue());
		}
		return this;
	}

	/**
	 * Set Header with single value
	 * If Header already exists, old value(s) will be replaced
	 */
	public SenderRequest setHeader(String name, Serializable value) {
		this.headers.set(name, toString(value));
		return this;
	}

	/**
	 * Set Header with multiple values
	 * If Header already exists, old value(s) will be replaced
	 */
	public <T extends Serializable> SenderRequest setHeader(String name, Serializable... values) {
		this.headers.set(name, toString(values));
		return this;
	}

	/**
	 * Add Header with single value
	 * If header already exists, new value will be added to it
	 */
	public SenderRequest addHeader(String name, Serializable value) {
		this.headers.add(name, toString(value));
		return this;
	}

	/**
	 * Add Header with multiple values
	 * If header already exists, new values will be added to it
	 */
	public <T extends Serializable> SenderRequest addHeader(String name, T... values) {
		this.headers.add(name, toString(values));
		return this;
	}

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

	public SenderRequest setParameters(Multival parameters) {
		this.parameters = parameters;
		return this;
	}

	/**
	 * Add parameter with single value
	 * If parameter already exists, new value will be added to it
	 */
	public SenderRequest addParam(String name, Serializable value) {
		this.parameters.add(name, toString(value));
		return this;
	}

	/**
	 * Add parameter with multiple values
	 * If parameter already exists, new values will be added to it
	 */
	public <T extends Serializable> SenderRequest addParam(String name, T... values) {
		this.parameters.add(name, toString(values));
		return this;
	}

	/**
	 * Set parameter with single value
	 * If Header already exists, old value(s) will be replaced
	 */
	public SenderRequest setParam(String name, Serializable value) {
		this.parameters.set(name, toString(value));
		return this;
	}

	/**
	 * Set parameter with multiple values
	 * If Header already exists, old value(s) will be replaced
	 */
	public <T extends Serializable> SenderRequest setParam(String name, T... values) {
		this.parameters.set(name, toString(values));
		return this;
	}

	/**
	 * Matrix parameter is allways part of URL
	 */
	public SenderRequest addMartixParam(String name, String value) {
		if (name.charAt(0) != ';') {
			name = ";" + name;
		}
		addParam(name, value);
		return this;
	}

	private static final String[] EMPTY = new String[0];

	private static final String[] BLANK = new String[] { "" };

	/**
	 * converts array of values to array of strings
	 */
	public static <T> String[] toString(T... values) {
		if (values != null && values.length != 0) {
			String[] strings = new String[values.length];
			for (int i = 0; i < values.length; ++i) {
				strings[i] = toString(values[i]);
			}
			return strings;
		} else {
			return BLANK; //XXX null handling
		}
	}

	/**
	 * converts single value to string
	 */
	public static String toString(Object value) {
		if (value == null) {
			return ""; //TODO handle null strategy
		} else if (value instanceof String) {
			return (String) value;
		} else if (value instanceof Number) {
			return String.valueOf(value);
		} else if (value instanceof Date) {
			return HttpDateUtil.formatDate((Date) value);
		} else {
			throw new IllegalArgumentException("Unsupported type : " + value.getClass().getName() + " of value: " + value);
		}
	}

	//XXX test this....
	public URI getUri() {
		if (sender == null) {
			return URI.create(urlPath);
		} else {
			URL url = sender.getConfig().getHostUrl();
			String[] pathAndQuery = sender.getPathAndQuery(this);
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
