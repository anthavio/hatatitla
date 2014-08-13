package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyWriter<B> {

	public void write(B payload, OutputStream stream) throws IOException;
}
