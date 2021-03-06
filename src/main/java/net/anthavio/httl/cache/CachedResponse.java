package net.anthavio.httl.cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;

import net.anthavio.httl.HttlException;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.util.Cutils;
import net.anthavio.httl.util.HttlUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachedResponse extends HttlResponse implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(CachedResponse.class.getName());

	private static final long serialVersionUID = 1L;

	private transient HttlRequest request; //do NOT store in cache

	private byte[] contentBinary;

	private String contentString;

	private static final InputStream DUMMY_STREAM = new ByteArrayInputStream(new byte[0]);

	/*
		protected CachedResponse() {
			//super();
			//serialization
		}
	*/
	public CachedResponse(HttlRequest request, HttlResponse response) {
		super(request, response.getHttpStatusCode(), response.getHttpStatusMessage(), response.getHeaders(), DUMMY_STREAM);
		this.request = request;
		try {
			if (response.isBinaryContent()) {
				contentBinary = HttlUtil.readAsBytes(response);
			} else {
				contentString = HttlUtil.readAsString(response);
			}
		} catch (IOException iox) {
			throw new HttlException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	//for testing purposes only - remove later
	public CachedResponse(HttlRequest request, int code, String message, Multival<String> headers, String data)
			throws IOException {
		super(request, code, message, headers, DUMMY_STREAM);
		this.contentString = data;
	}

	@Override
	public void close() {
		//nothing
	}

	public HttlRequest getRequest() {
		return request;
	}

	public void setRequest(HttlRequest request) {
		this.request = request;
	}

	public boolean isBinaryContent() {
		return contentBinary != null;
	}

	@Override
	public InputStream getStream() {
		if (contentBinary != null) {
			return new ByteArrayInputStream(contentBinary);
		} else {
			logger.warn("Inefficient conversion from string to bytes. Consider using getReader() method instead");
			return new ByteArrayInputStream(contentString.getBytes(getCharset()));
		}
	}

	@Override
	public Reader getReader() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string. Consider using getStream() method instead");
			return new StringReader(new String(contentBinary, getCharset()));
		} else {
			return new StringReader(contentString);
		}
	}

	public byte[] getAsBytes() {
		if (contentBinary != null) {
			return contentBinary;
		} else {
			logger.warn("Inefficient conversion from string to bytes. Consider using getAsString() method instead");
			return contentString.getBytes(getCharset());
		}
	}

	public String getAsString() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string. Consider using getAsBytes() method instead");
			return new String(contentBinary, getCharset());
		} else {
			return contentString;
		}
	}

	@Override
	public String toString() {
		return "CachedResponse#" + hashCode() + "{" + getHttpStatusCode() + ", " + getHttpStatusMessage() + "}";
	}

}
