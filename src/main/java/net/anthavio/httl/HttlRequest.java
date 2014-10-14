package net.anthavio.httl;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.List;

import net.anthavio.httl.HttlBody.Type;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.HttlUtil;

/**
 * Immutable HTTP request
 * 
 * @author martin.vanek
 *
 */
public class HttlRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	// http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html
	// http://en.wikipedia.org/wiki/Hypertext_Transfer_Protocol
	public static enum Method {
		GET(false, true, true), HEAD(false, true, true), //
		OPTIONS(false, true, true), TRACE(false, true, true), //
		POST(true, false, false), PUT(true, false, true), //
		PATCH(true, false, false), DELETE(false, false, true);

		private final boolean bodyAllowed;
		private final boolean safe;
		private final boolean idempotent; //repeatable

		private Method(boolean canHaveBody, boolean safe, boolean idempotent) {
			this.bodyAllowed = canHaveBody;
			this.safe = safe;
			this.idempotent = idempotent;
		}

		public boolean isBodyAllowed() {
			return this.bodyAllowed;
		}
	}

	protected static final String DEFAULT_URI = "/";

	protected transient HttlSender sender;

	private final Method method;

	private final String urlPath;

	private final String pathAndQuery; // urlPath + query

	private final Multival<String> headers;

	private final String[] contentType;

	private final Multival<String> parameters;

	private final HttlBody body;

	private final Integer readTimeoutMillis; //millis - override config value

	protected HttlRequest(HttlSender sender, Method method, String urlPath) {
		this(sender, method, urlPath, null, null, null, null);
	}

	public HttlRequest(HttlSender sender, Method method, String urlPath, Multival<String> parameters,
			Multival<String> headers, HttlBody body, Integer readTimeoutMillis) {

		if (sender == null) {
			throw new IllegalArgumentException("Null sender");
		}
		this.sender = sender;
		SenderConfigurer config = sender.getConfig();

		if (method == null) {
			throw new IllegalArgumentException("Null method");
		}
		this.method = method;

		if (Cutils.isEmpty(urlPath)) {
			throw new IllegalArgumentException("Empty url path");
		}
		this.urlPath = urlPath;

		if (parameters != null) {
			this.parameters = parameters;
		} else {
			this.parameters = new Multival<String>();
		}

		Multival<String> defaultParams = config.getDefaultParameters();
		for (String name : defaultParams) {
			if (this.parameters.get(name) == null) {
				this.parameters.set(name, defaultParams.get(name));
			}
		}

		if (headers != null) {
			this.headers = headers;
		} else {
			this.headers = new Multival<String>();
		}

		Multival<String> defaultHeaders = config.getDefaultHeaders();
		for (String name : defaultHeaders) {
			if (this.headers.get(name) == null) {
				this.headers.set(name, defaultHeaders.get(name));
			}
		}

		String[] pathAndQuery = buildUrlPathAndQuery(urlPath, this.parameters);
		final String path = HttlUtil.joinUrlParts(config.getUrl().getPath(), pathAndQuery[0]);
		final String query = pathAndQuery[1];

		String reqConTyp = this.headers.getFirst(HttlConstants.Content_Type);
		this.contentType = HttlUtil.splitContentType(reqConTyp, sender.getConfig().getCharset());

		//TODO multipart/form-data

		if (method.bodyAllowed) { // POST, PUT
			if (body != null) {

				if (this.contentType[0] == null) {
					throw new HttlRequestException("Request with body must have Content-Type: " + this);
				}

				if (query != null) {
					this.pathAndQuery = path + "?" + query;
				} else {
					this.pathAndQuery = path;
				}

				this.body = body;

			} else if (query != null) {
				//Simple POST form submission
				if (this.contentType[0] == null) {
					this.contentType[0] = "application/x-www-form-urlencoded";
					headers.set(HttlConstants.Content_Type, this.contentType[0] + "; charset=" + this.contentType[1]);
				}
				this.body = new HttlBody(query);
				this.pathAndQuery = path;

			} else {
				//no body & no query - Content-Type not really needed
				this.body = null;
				this.pathAndQuery = path;
			}

			if (sender.getConfig().isSkipCharset() && contentType[0] != null) {
				//Some wierdos dislike charset in Content-Type header
				headers.set(HttlConstants.Content_Type, this.contentType[0]);
			}

		} else { // GET, HEAD, ...
			if (body != null) {
				throw new HttlRequestException("Method " + method + " cannot have body: " + this);
			}
			this.body = null;
			if (query != null) {
				this.pathAndQuery = path + "?" + query;
			} else {
				this.pathAndQuery = path;
			}
		}

		this.readTimeoutMillis = readTimeoutMillis;
	}

	public HttlSender getSender() {
		return sender;
	}

	public Method getMethod() {
		return this.method;
	}

	public String getUrlPath() {
		return urlPath;
	}

	/**
	 * @return url file is path and query
	 */
	public String getPathAndQuery() {
		return this.pathAndQuery;
	}

	/**
	 * @return complete url
	 */
	public URL getUrl() {
		URL baseurl = sender.getConfig().getUrl();
		try {
			return new URL(baseurl.getProtocol(), baseurl.getHost(), baseurl.getPort(), this.pathAndQuery);
		} catch (MalformedURLException mux) {
			throw new IllegalStateException("Broken " + baseurl + " " + this.pathAndQuery, mux);
		}
	}

	/**
	 * Message Body stream or null
	 */
	public HttlBody getBody() {
		return body;
	}

	public Multival<String> getHeaders() {
		return this.headers;
	}

	/**
	 * @return media type part of the Content-Type header
	 */
	public String getMediaType() {
		return contentType[0];
	}

	/**
	 * @return charset part of the Content-Type header
	 */
	public String getCharset() {
		return contentType[1];
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

	public Multival<String> getParameters() {
		return this.parameters;
	}

	// configuration

	public Integer getReadTimeoutMillis() {
		return readTimeoutMillis;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.method == null ? 0 : this.method.hashCode());
		result = prime * result + (this.pathAndQuery == null ? 0 : this.pathAndQuery.hashCode());
		result = prime * result + (this.headers == null ? 0 : this.headers.hashCode());
		result = prime * result + (this.body == null ? 0 : this.body.hashCode());
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
		HttlRequest other = (HttlRequest) obj;
		if (this.method != other.method) {
			return false;
		}
		if (this.pathAndQuery == null) {
			if (other.pathAndQuery != null) {
				return false;
			}
		} else if (!this.pathAndQuery.equals(other.pathAndQuery)) {
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

	public void update(MessageDigest digest) {
		digest.update(method.toString().getBytes());
		digest.update(pathAndQuery.getBytes());
		Iterator<String> iterator = headers.iterator();
		while (iterator.hasNext()) {
			String name = iterator.next();
			digest.update(name.getBytes());
			List<String> list = headers.get(name);
			for (String value : list) {
				digest.update(value.getBytes());
			}
		}
		if (body != null) {
			if (body.getType() == Type.BYTES) {
				digest.update((byte[]) body.getPayload());
			} else if (body.getType() == Type.STRING) {
				digest.update(((String) body.getPayload()).getBytes());
			} else {
				throw new IllegalStateException("Cannot hash streaming body: " + body.getType());
			}
		}

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(method);
		sb.append(' ');
		sb.append(getUrl());
		if (parameters != null) {
			sb.append(' ');
			sb.append(parameters);
		}
		return sb.toString();
	}

	/**
	 * @return array of 2 elements 
	 */
	protected static String[] buildUrlPathAndQuery(String urlPath, Multival<String> parameters) {
		StringBuilder path = new StringBuilder(urlPath);
		StringBuilder query = new StringBuilder();

		for (String name : parameters) {
			List<String> values = parameters.get(name);

			for (String value : values) {
				if (name.charAt(0) == '{') { //path parameter starts with {
					int idx = path.indexOf(name);
					if (idx == -1) {
						throw new IllegalArgumentException("Path parameter " + name + " not found in " + path);
					}
					value = HttlUtil.urlencode(value);
					path.replace(idx, idx + name.length(), value);

				} else if (name.charAt(0) == ';') { //matrix parameter starts with ;
					path.append(';').append(HttlUtil.urlencode(name.substring(1))).append('=').append(HttlUtil.urlencode(value));

				} else { // query parameter
					query.append('&').append(HttlUtil.urlencode(name)).append('=').append(HttlUtil.urlencode(value));
				}
			}
		}

		if (path.indexOf("{") != -1) {
			throw new IllegalStateException("Unresolved path parameter found: " + path);
		}
		return new String[] { path.toString(), query.length() == 0 ? null : query.substring(1) };
	}

}
