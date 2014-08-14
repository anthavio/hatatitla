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

	private final HttlResponse response;

	public HttlProcessingException(HttlResponse response, Exception x) {
		super(x);
		this.response = response;
	}

	public HttlProcessingException(HttlResponse response, String message) {
		super(message);
		this.response = response;
	}

	public HttlResponse getResponse() {
		return response;
	}

}
