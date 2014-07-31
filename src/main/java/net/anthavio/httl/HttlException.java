package net.anthavio.httl;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Exception delegate;

	protected HttlException(String message) {
		super(message);
		this.delegate = null;
	}

	public HttlException(Exception x) {
		super(x);
		this.delegate = x;
	}

	public Exception getException() {
		return delegate;
	}

	/**
	 * If connect refused | connect timeout | read timeout
	 */
	public boolean isConnectOrReadTimeout() {
		return delegate instanceof ConnectException || delegate instanceof SocketTimeoutException;
	}

	@Override
	public void printStackTrace() {
		if (delegate != null) {
			delegate.printStackTrace();
		} else {
			super.printStackTrace();
		}
	}

	@Override
	public void printStackTrace(PrintStream s) {
		if (delegate != null) {
			delegate.printStackTrace(s);
		} else {
			super.printStackTrace(s);
		}
	}

	@Override
	public void printStackTrace(PrintWriter s) {
		if (delegate != null) {
			delegate.printStackTrace(s);
		} else {
			super.printStackTrace(s);
		}
	}

	@Override
	public StackTraceElement[] getStackTrace() {
		if (delegate != null) {
			return delegate.getStackTrace();
		} else {
			return super.getStackTrace();
		}
	}

}
