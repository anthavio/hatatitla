package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 * 
 * When response cannot/failed to be processed/extracted & wrapping IOExceptions
 *
 */
public class HttlProcessingException extends HttlException {

	private static final long serialVersionUID = 1L;

	public HttlProcessingException(Exception x) {
		super(x);
	}

	public HttlProcessingException(String message) {
		super(message);
	}

}
