package net.anthavio.httl;

import java.io.IOException;
import java.io.OutputStream;

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
	public abstract void marshall(HttlRequest request, OutputStream stream) throws IOException;

}
