package com.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

import com.google.gson.Gson;

/**
 * Java bean -> JSON Request body marshaller
 * 
 * https://code.google.com/p/google-gson/
 * 
 * @author martin.vanek
 *
 */
public class GsonRequestMarshaller implements RequestBodyMarshaller {

	private final Gson gson;

	public GsonRequestMarshaller() {
		this.gson = new Gson();
	}

	public GsonRequestMarshaller(Gson gson) {
		if (gson == null) {
			throw new IllegalArgumentException("null gson");
		}
		this.gson = gson;
	}

	public Gson getGson() {
		return gson;
	}

	@Override
	public String marshall(Object requestBody) throws IOException {
		StringWriter swriter = new StringWriter();
		gson.toJson(requestBody, swriter);
		return swriter.toString();
	}

	@Override
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		gson.toJson(requestBody, writer);
	}

	@Override
	public String toString() {
		return "SimpleXmlRequestMarshaller [gson=" + gson + "]";
	}

}
