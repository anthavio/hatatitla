package net.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.anthavio.httl.HttlSender.HttlHeaders;
import net.anthavio.httl.util.HttpHeaderUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class HttlResponse implements Closeable, Serializable {

	private static final Logger logger = LoggerFactory.getLogger(HttlResponse.class);

	private static final long serialVersionUID = 1L;

	protected final HttlRequest request;

	protected final int httpStatusCode;

	protected final String httpStatusMessage;

	protected final HttlHeaders headers;

	protected transient InputStream stream;

	protected final String mediaType;

	protected final String encoding;// = "utf-8";//"ISO-8859-1";

	protected boolean closed;

	public HttlResponse(HttlRequest request, int httpCode, String message, HttlHeaders headers, InputStream stream) {
		this.request = request;
		this.httpStatusCode = httpCode;
		this.httpStatusMessage = message;
		this.headers = headers;

		String contentType = headers.getFirst("Content-Type");
		if (contentType != null) {
			String[] parts = HttpHeaderUtil.splitContentType(contentType, "utf-8");
			this.mediaType = parts[0];
			this.encoding = parts[1];
		} else {
			this.mediaType = null;
			this.encoding = "utf-8";
		}

		String responseEncoding = headers.getFirst("Content-Encoding");
		if (stream != null && responseEncoding != null) {
			if (responseEncoding.indexOf("gzip") != -1) {
				try {
					stream = new GZIPInputStream(stream);
				} catch (IOException iox) {
					throw new HttlProcessingException(this, iox);
				}
			} else if (responseEncoding.indexOf("deflate") != -1) {
				stream = new InflaterInputStream(stream);
			}
		}
		this.stream = stream; //null for 304 Not Modified

	}

	public HttlRequest getRequest() {
		return request;
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getHttpStatusMessage() {
		return httpStatusMessage;
	}

	public HttlHeaders getHeaders() {
		return headers;
	}

	public String getFirstHeader(String string) {
		if (headers != null) {
			return headers.getFirst(string);
		} else {
			return null;
		}
	}

	public InputStream getStream() {
		return stream;
	}

	public Reader getReader() {
		return new InputStreamReader(stream, getCharset());
	}

	/**
	 * @return guess if Content-Type is NOT of any known textual media types.
	 */
	public boolean isBinaryContent() {
		return !HttpHeaderUtil.isTextContent(mediaType);
	}

	/**
	 * @return charset part of the Content-Type header
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @return charset part of the Content-Type header
	 */
	public Charset getCharset() {
		return Charset.forName(encoding);
	}

	/**
	 * @return media type part of the Content-Type header
	 */
	public String getMediaType() {
		return mediaType;
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			try {
				HttpHeaderUtil.close(this);
			} catch (IOException iox) {
				logger.warn("Closing problem: " + iox);
			}
		}
	}

	@Override
	public String toString() {
		return "HttlResponse {" + httpStatusCode + ", " + httpStatusMessage + ", " + mediaType + ", " + encoding + "}";
	}

}
