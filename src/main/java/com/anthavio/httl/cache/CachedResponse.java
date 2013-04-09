package com.anthavio.httl.cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.httl.Cutils;
import com.anthavio.httl.HttpHeaderUtil;
import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.SenderException;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachedResponse extends SenderResponse implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(CachedResponse.class.getName());

	private static final long serialVersionUID = 1L;

	private transient SenderRequest request; //do NOT store in cache

	private byte[] contentBinary;

	private String contentString;

	private static final InputStream DUMMY_STREAM = new ByteArrayInputStream(new byte[0]);

	protected CachedResponse() {
		super();
		//serialization
	}

	public CachedResponse(SenderRequest request, SenderResponse response) {
		super(response.getHttpStatusCode(), response.getHttpStatusMessage(), response.getHeaders(), DUMMY_STREAM);
		this.request = request;
		try {
			if (response.isBinaryContent()) {
				contentBinary = HttpHeaderUtil.readAsBytes(response);
			} else {
				contentString = HttpHeaderUtil.readAsString(response);
			}
		} catch (IOException iox) {
			throw new SenderException(iox);
		} finally {
			Cutils.close(response);
		}
	}

	//for testing purposes only - remove later
	public CachedResponse(int code, String message, Multival headers, String data) throws IOException {
		super(code, message, headers, DUMMY_STREAM);
		this.contentString = data;
	}

	@Override
	public void close() throws IOException {
		//nothing
	}

	public SenderRequest getRequest() {
		return request;
	}

	public void setRequest(SenderRequest request) {
		this.request = request;
	}

	@Override
	public InputStream getStream() {
		if (contentBinary != null) {
			return new ByteArrayInputStream(contentBinary);
		} else {
			logger.warn("Inefficient conversion from string to bytes");
			return new ByteArrayInputStream(contentString.getBytes(getCharset()));
		}
	}

	@Override
	public Reader getReader() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string");
			return new StringReader(new String(contentBinary, getCharset()));
		} else {
			return new StringReader(contentString);
		}
	}

	public byte[] getAsBytes() {
		if (contentBinary != null) {
			return contentBinary;
		} else {
			logger.warn("Inefficient conversion from string to bytes");
			return contentString.getBytes(getCharset());
		}
	}

	public String getAsString() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string");
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
