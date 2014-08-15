package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Used in @RestBody writer attribute  
 * 
 * Example:
 * 
 * @RestCall("POST /something")
 * public String something(@RestBody(writer=MyGreatResultSetBodyWriter.class) ResultSet resultset);
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
