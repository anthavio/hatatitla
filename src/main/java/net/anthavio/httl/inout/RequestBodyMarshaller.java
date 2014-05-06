package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * 
 * @author martin.vanek
 *
 */
public interface RequestBodyMarshaller {

	/**
	 * Marshall Request body object into String
	 */
	public String marshall(Object requestBody) throws IOException;

	/**
	 * Marshall Request body object directly into OutputStream. Do not close the stream.
	 */
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException;

}
