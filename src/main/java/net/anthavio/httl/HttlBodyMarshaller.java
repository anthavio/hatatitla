package net.anthavio.httl;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyMarshaller {

	/**
	 * Check request.getMediaType() and marshall request.getBody().getPayload()
	 */
	public abstract void marshall(Object payload, String mediaType, String charset, OutputStream stream)
			throws IOException;

}
