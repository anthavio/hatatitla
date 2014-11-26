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

import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.util.HttlUtil;

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

	protected final Multival<String> headers;

	protected transient InputStreamWrapper stream;

	protected final String mediaType;

	protected final String encoding;// = "utf-8";//"ISO-8859-1";

	public HttlResponse(HttlRequest request, int httpCode, String message, Multival<String> headers, InputStream stream) {
		this.request = request;
		this.httpStatusCode = httpCode;
		this.httpStatusMessage = message;
		this.headers = headers;

		String contentType = headers.getFirst("Content-Type");
		if (contentType != null) {
			String[] parts = HttlUtil.splitContentType(contentType, "utf-8");
			this.mediaType = parts[0];
			this.encoding = parts[1];
		} else {
			this.mediaType = null;
			this.encoding = "utf-8";
		}

		if (stream != null) {
			String responseEncoding = headers.getFirst("Content-Encoding");
			if (responseEncoding != null) {
				if (responseEncoding.indexOf("gzip") != -1) {
					try {
						stream = new GZIPInputStream(stream);
					} catch (IOException iox) {
						throw new HttlResponseException(this, iox);
					}
				} else if (responseEncoding.indexOf("deflate") != -1) {
					stream = new InflaterInputStream(stream);
				}
			}
			this.stream = new InputStreamWrapper(stream);
		} else {
			this.stream = null; //null for 304 Not Modified
		}
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

	public Multival<String> getHeaders() {
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
		return new InputStreamReader(getStream(), getCharset());
	}

	/**
	 * @return guess if Content-Type is NOT of any known textual media types.
	 */
	public boolean isBinaryContent() {
		return !HttlUtil.isTextContent(mediaType);
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
		if (stream != null && !stream.isClosed()) {
			try {
				HttlUtil.close(this);
			} catch (IOException iox) {
				logger.warn("Closing problem: " + iox);
			}
		}
	}

	@Override
	public String toString() {
		return "HttlResponse {" + httpStatusCode + ", " + httpStatusMessage + ", " + mediaType + ", " + encoding + "}";
	}

	/**
	 * Tracking close call to prevent IOException
	 * 
	 * @author martin.vanek
	 *
	 */
	class InputStreamWrapper extends InputStream {

		private final InputStream stream;

		private boolean closed = false;

		InputStreamWrapper(InputStream stream) {
			this.stream = stream;
		}

		@Override
		public void close() throws IOException {
			closed = true;
			stream.close();
		}

		public boolean isClosed() {
			return closed;
		}

		@Override
		public int read() throws IOException {
			return stream.read();
		}

		public int read(byte[] b, int off, int len) throws IOException {
			return stream.read(b, off, len);
		}

		public int read(byte[] b) throws IOException {
			return stream.read(b);
		}

		public int available() throws IOException {
			return stream.available();
		}

		public boolean equals(Object obj) {
			return stream.equals(obj);
		}

		public int hashCode() {
			return stream.hashCode();
		}

		public void mark(int readlimit) {
			stream.mark(readlimit);
		}

		public boolean markSupported() {
			return stream.markSupported();
		}

		public void reset() throws IOException {
			stream.reset();
		}

		public long skip(long n) throws IOException {
			return stream.skip(n);
		}

		public String toString() {
			return stream.toString();
		}
	}

}
