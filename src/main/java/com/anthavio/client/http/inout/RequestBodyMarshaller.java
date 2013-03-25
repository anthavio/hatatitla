package com.anthavio.client.http.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * 
 * @author martin.vanek
 *
 */
public interface RequestBodyMarshaller {

	public String marshall(Object requestBody) throws IOException;

	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException;

}
