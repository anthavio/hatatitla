package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 * 
 * When HttlRequest cannot be built or send - missing/broken request marshaller for example
 *
 */
public class HttlRequestException extends HttlException {

	private static final long serialVersionUID = 1L;

	public HttlRequestException(Exception x) {
		super(x);
	}

	public HttlRequestException(String message) {
		super(message);
	}

}
