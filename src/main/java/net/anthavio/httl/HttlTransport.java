package net.anthavio.httl;

import java.io.Closeable;
import java.io.IOException;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlTransport extends Closeable {

	public HttlResponse call(HttlRequest requste) throws IOException;

	/**
	 * Redeclare Closeable to surpress IOException
	 */
	public void close();

}
