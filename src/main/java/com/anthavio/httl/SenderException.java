package com.anthavio.httl;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * 
 * @author martin.vanek
 *
 */
public class SenderException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private IOException delegate;

	public SenderException(String message) {
		super(message);
	}

	public SenderException(IOException iox) {
		super(iox.getMessage());
		this.delegate = iox;
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
