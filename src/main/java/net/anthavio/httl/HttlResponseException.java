package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 * 
 * When response cannot/failed to be processed/extracted & wrapping IOExceptions
 *
 */
public class HttlResponseException extends HttlException {

	private static final long serialVersionUID = 1L;

	private final HttlResponse response;

	public HttlResponseException(HttlResponse response, Exception x) {
		super(x);
		this.response = response;
	}

	public HttlResponseException(HttlResponse response, String message) {
		super(message);
		this.response = response;
	}

	public HttlResponse getResponse() {
		return response;
	}

}
