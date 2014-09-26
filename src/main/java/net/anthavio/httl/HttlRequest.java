package net.anthavio.httl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import net.anthavio.httl.HttlBody.Type;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * Immutable
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

	private final Multival<String> headers;

	private final String[] contentType;

	private final Multival<String> parameters;

	private HttlBody body;

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

		if (parameters != null) {
			this.parameters = parameters;
		} else {
			this.parameters = new Multival<String>();
		}

		Multival<String> defaultParams = config.getDefaultParameters();
		for (String name : defaultParams) {
			if (parameters.get(name) == null) {
				parameters.set(name, defaultParams.get(name));
			}
		}

		if (headers != null) {
			this.headers = headers;
		} else {
			this.headers = new Multival<String>();
		}

		Multival<String> defaultHeaders = config.getDefaultHeaders();
		for (String name : defaultHeaders) {
			if (headers.get(name) == null) {
				headers.set(name, defaultHeaders.get(name));
			}
		}

		String[] upaq = buildUrlPathAndQuery(urlPath, parameters);
		String path = HttpHeaderUtil.joinUrlParts(config.getUrl().getPath(), upaq[0]);
		String query = upaq[1];

		digContentType(defaultHeaders, sender.getConfig());
		String contentType = headers.getFirst(HttlConstants.Content_Type);
		this.contentType = digContentType(contentType,
				sender.getConfig().getDefaultHeaders().getFirst(HttlConstants.Content_Type), sender.getConfig().getCharset());

		//TODO multipart/form-data

		if (method.bodyAllowed) { // POST, PUT
			if (body != null) {
				if (this.contentType[0] == null) {
					throw new HttlRequestException("Request with body must have media type");
				}

				if (query != null) {
					this.pathAndQuery = path + "?" + query;
				} else {
					this.pathAndQuery = path;
				}

				String contentTypeValue;
				if (sender.getConfig().isSkipCharset()) {
					contentTypeValue = this.contentType[0];
				} else {
					contentTypeValue = this.contentType[0] + "; charset=" + this.contentType[1];
				}
				//Set header before asking for Marshaller
				headers.set(HttlConstants.Content_Type, contentTypeValue);

				this.body = body;
				if (body.isBuffer()) {
					try {
						if (body.getType() == Type.MARSHALL) {
							ByteArrayOutputStream baos = new ByteArrayOutputStream();

							sender.getConfig().getMarshaller().marshall(this, baos);
							this.body = new HttlBody(baos.toByteArray());
						} else if (body.getType() == Type.STREAM) {
							byte[] bytes = HttpHeaderUtil.readAsBytes((InputStream) body.getPayload(), HttpHeaderUtil.KILO16);
							this.body = new HttlBody(bytes);
						} else if (body.getType() == Type.READER) {
							String string = HttpHeaderUtil.readAsString((Reader) body.getPayload(), HttpHeaderUtil.KILO16);
							this.body = new HttlBody(string);
						}
					} catch (IOException iox) {
						throw new HttlRequestException(iox);
					}
				}

			} else if (query != null) {
				//Simple POST form submission
				if (this.contentType[0] == null) {
					this.contentType[0] = "application/x-www-form-urlencoded";
				}
				this.body = new HttlBody(query);
				this.pathAndQuery = path;
				String contentTypeValue;
				if (sender.getConfig().isSkipCharset()) {
					contentTypeValue = this.contentType[0];
				} else {
					contentTypeValue = this.contentType[0] + "; charset=" + this.contentType[1];
				}
				headers.set(HttlConstants.Content_Type, contentTypeValue);

			} else {
				//no body & no query - Content-Type not needed
				this.body = null;
				this.pathAndQuery = path;
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

	protected static String[] buildUrlPathAndQuery(String urlPath, Multival<String> parameters) {
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

	public static String[] digContentType(Multival<String> headers, SenderConfigurer config) {
		return digContentType(headers.getFirst(HttlConstants.Content_Type),
				config.getDefaultHeaders().getFirst(HttlConstants.Content_Type), config.getCharset());
	}

	/**
	 * @param requestContentType - can be null
	 * @param defaultMediaType - can be null
	 * @param defaultCharset = never null
	 * @return 
	 */
	public static String[] digContentType(String requestContentType, String defaultContentType, String defaultCharset) {
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
		sb.append(getUrl());
		if (parameters != null) {
			sb.append(' ');
			sb.append(parameters);
		}
		return sb.toString();
	}
}
