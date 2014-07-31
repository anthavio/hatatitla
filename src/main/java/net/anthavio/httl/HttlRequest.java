package net.anthavio.httl;

import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import net.anthavio.httl.HttlSender.HttpHeaders;
import net.anthavio.httl.HttlSender.Parameters;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
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

	private final String pathAndQuery; // path + query

	private final HttpHeaders headers;

	protected final String[] contentType;

	private final Parameters parameters;

	private final InputStream bodyStream;

	private final Integer readTimeoutMillis; //millis - override config value

	protected HttlRequest(HttlSender sender, Method method, String urlPath) {
		this(sender, method, urlPath, null, null, null, null);
	}

	public HttlRequest(HttlSender sender, Method method, String urlPath, Parameters parameters, HttpHeaders headers,
			InputStream bodyStream, Integer readTimeoutMillis) {

		if (sender == null) {
			throw new IllegalArgumentException("Null sender");
		}
		this.sender = sender;
		SenderBuilder config = sender.getConfig();

		if (method == null) {
			throw new IllegalArgumentException("null method");
		}
		this.method = method;

		if (Cutils.isEmpty(urlPath)) {
			throw new IllegalArgumentException("Empty url path");
		}

		if (parameters != null) {
			this.parameters = parameters;
		} else {
			this.parameters = new Parameters();
		}

		Parameters defaultParams = config.getDefaultParameters();
		for (String name : defaultParams) {
			if (parameters.get(name) == null) {
				parameters.set(name, defaultParams.get(name));
			}
		}

		if (headers != null) {
			this.headers = headers;
		} else {
			this.headers = new HttpHeaders();
		}

		HttpHeaders defaultHeaders = config.getDefaultHeaders();
		for (String name : defaultHeaders) {
			if (headers.get(name) == null) {
				headers.set(name, defaultHeaders.get(name));
			}
		}

		String[] upaq = buildUrlPathAndQuery(urlPath, parameters);
		String path = HttpHeaderUtil.joinUrlParts(config.getUrl().getPath(), upaq[0]);
		String query = upaq[1];

		String contentType = headers.getFirst(HttlConstants.Content_Type);
		this.contentType = pickContentType(contentType,
				sender.getConfig().getDefaultHeaders().getFirst(HttlConstants.Content_Type), sender.getConfig().getEncoding());

		//TODO multipart/form-data

		if (method.bodyAllowed) { // POST, PUT
			if (bodyStream != null) {
				if (this.contentType[0] == null) {
					throw new HttlRequestException("Request with body must have media type");
				}
				this.bodyStream = bodyStream;
				if (query != null) {
					this.pathAndQuery = path + "?" + query;
				} else {
					this.pathAndQuery = path;
				}

			} else if (query != null) {
				//Simple POST form submission
				if (this.contentType[0] == null) {
					this.contentType[0] = "application/x-www-form-urlencoded";
				}
				this.bodyStream = new PseudoStream(query);
				this.pathAndQuery = path;
			} else {
				//no body & no query - Content-Type type needed
				this.bodyStream = null;
				this.pathAndQuery = path;

			}
		} else { // GET, HEAD, ...
			if (bodyStream != null) {
				throw new HttlRequestException("Method " + method + " cannot have body: " + this);
			}
			this.bodyStream = null;
			if (query != null) {
				this.pathAndQuery = path + "?" + query;
			} else {
				this.pathAndQuery = path;
			}
		}

		if (this.contentType[0] != null) {
			headers.set(HttlConstants.Content_Type, this.contentType[0] + "; charset=" + this.contentType[1]);
		}

		this.readTimeoutMillis = readTimeoutMillis;
	}

	/**
	 * Way to attach Request to Sender if it was not done through constructor
	 TODO remove ass obsolete
	public void setSender(HttpSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("sender is null");
		}
		this.sender = sender;
	}
	*/

	public HttlSender getSender() {
		return sender;
	}

	public Method getMethod() {
		return this.method;
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
	public InputStream getBodyStream() {
		return bodyStream;
	}

	public HttpHeaders getHeaders() {
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

	public Parameters getParameters() {
		return this.parameters;
	}

	// configuration

	public Integer getReadTimeoutMillis() {
		return readTimeoutMillis;
	}

	protected static String[] buildUrlPathAndQuery(String urlPath, Parameters parameters) {
		StringBuilder path = new StringBuilder(urlPath);
		StringBuilder query = new StringBuilder();

		for (String name : parameters) {
			List<String> values = parameters.get(name);

			for (String value : values) {
				if (name.charAt(0) == '{') { //path parameter
					int idx = path.indexOf(name);
					if (idx == -1) {
						throw new IllegalArgumentException("Path parameter " + name + " not found in " + path);
					}
					value = HttpHeaderUtil.urlencode(value);
					path.replace(idx, idx + name.length(), value);

				} else if (name.charAt(0) == ';') { //matrix parameter
					path.append(';').append(HttpHeaderUtil.urlencode(name.substring(1))).append('=')
							.append(HttpHeaderUtil.urlencode(value));

				} else { // query parameter
					query.append('&').append(HttpHeaderUtil.urlencode(name)).append('=').append(HttpHeaderUtil.urlencode(value));
				}
			}
		}

		if (path.indexOf("{") != -1) {
			throw new IllegalStateException("Unresolved path parameter found: " + path);
		}
		return new String[] { path.toString(), query.length() == 0 ? null : query.substring(1) };
	}

	public static final String urlencode(String string) {
		try {
			return URLEncoder.encode(string, "utf-8"); //W3C recommends utf-8 
		} catch (UnsupportedEncodingException uex) {
			throw new IllegalStateException("Misconfigured encoding utf-8", uex);
		}
	}

	/**
	 * @param requestContentType - can be null
	 * @param defaultMediaType - can be null
	 * @param defaultCharset = never null
	 * @return 
	 */
	private static String[] pickContentType(String requestContentType, String defaultContentType, String defaultCharset) {
		String mediaType;
		String charset;
		if (requestContentType != null) {

			int idxMediaEnd = requestContentType.indexOf(";");
			if (idxMediaEnd != -1) {
				mediaType = requestContentType.substring(0, idxMediaEnd);
			} else {
				mediaType = requestContentType;
			}

			int idxCharset = requestContentType.indexOf("charset=");
			if (idxCharset != -1) {
				charset = requestContentType.substring(idxCharset + 8);
			} else {
				charset = defaultCharset;
			}
		} else if (defaultContentType != null) {
			int idxMediaEnd = defaultContentType.indexOf(";");
			if (idxMediaEnd != -1) {
				mediaType = defaultContentType.substring(0, idxMediaEnd);
			} else {
				mediaType = defaultContentType;
			}
			int idxCharset = defaultContentType.indexOf("charset=");
			if (idxCharset != -1) {
				charset = defaultContentType.substring(idxCharset + 8);
			} else {
				charset = defaultCharset;
			}
		} else {
			mediaType = null;
			charset = defaultCharset;
		}

		return new String[] { mediaType, charset };
	}

	//XXX test this....
	/*
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
	*/
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.method == null ? 0 : this.method.hashCode());
		result = prime * result + (this.pathAndQuery == null ? 0 : this.pathAndQuery.hashCode());
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(method);
		sb.append(' ');
		if (sender != null) {
			sb.append(sender.getConfig().getUrl() + pathAndQuery);
		} else {
			sb.append(pathAndQuery);
		}
		sb.append(' ');
		sb.append(parameters);
		return sb.toString();
	}

}
