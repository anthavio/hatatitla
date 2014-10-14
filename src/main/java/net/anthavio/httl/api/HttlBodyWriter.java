package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Used in @HttlBody writer attribute  
 * 
 * Example:
 * 
 * @HttlCall("POST /something")
 * public String something(@HttlBody(writer=MyGreatResultSetBodyWriter.class) ResultSet resultset);
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyWriter<B> {

	/**
	 * Write payload into stream in most crazy way you can find
	 */
	public void write(B payload, OutputStream stream) throws IOException;

}
