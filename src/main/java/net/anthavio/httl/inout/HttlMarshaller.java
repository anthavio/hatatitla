package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlMarshaller {

	/**
	 * Marshall Request payload object directly into OutputStream. Do not close the stream.
	 */
	public void write(Object payload, OutputStream stream, Charset charset) throws IOException;

}
