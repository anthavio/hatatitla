package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;

import com.google.gson.Gson;

/**
 * Java bean -> JSON Request body marshaller
 * 
 * https://code.google.com/p/google-gson/
 * 
 * @author martin.vanek
 *
 */
public class GsonMarshaller implements HttlBodyMarshaller {

	private final Gson gson;

	public GsonMarshaller() {
		this.gson = new Gson();
		//this.gson = new GsonBuilder().setDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").create();
	}

	public GsonMarshaller(Gson gson) {
		if (gson == null) {
			throw new IllegalArgumentException("null gson");
		}
		this.gson = gson;
	}

	public Gson getGson() {
		return gson;
	}

	@Override
	public GsonMarshaller supports(HttlRequest request) {
		return request.getMediaType().contains("json") ? this : null;
	}

	@Override
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		gson.toJson(requestBody, writer);
		writer.flush();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [gson=" + gson + "]";
	}

}
