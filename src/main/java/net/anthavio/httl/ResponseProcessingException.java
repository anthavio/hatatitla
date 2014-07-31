package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 * 
 * When response cannot/failed to be processed/extracted & wrapping IOExceptions
 *
 */
public class ResponseProcessingException extends HttlException {

	private static final long serialVersionUID = 1L;

	public ResponseProcessingException(Exception x) {
		super(x);
	}

	public ResponseProcessingException(String message) {
		super(message);
	}

}
