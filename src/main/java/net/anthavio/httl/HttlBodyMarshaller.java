package net.anthavio.httl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Work like Factory (supports) and Marshaller (write) in the same time
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyMarshaller {

	/**
	 * Kind of lookup method
	 * @return null or instance that will marshall request
	 */
	public abstract HttlBodyMarshaller supports(HttlRequest request);

	/**
	 * Marshall Request payload object directly into OutputStream. Do not close the stream.
	 */
	public void write(Object payload, OutputStream stream, Charset charset) throws IOException;

}
