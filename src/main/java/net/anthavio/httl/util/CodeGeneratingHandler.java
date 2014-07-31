package net.anthavio.httl.util;

import java.io.IOException;

import net.anthavio.httl.HttlResponseHandler;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;

/**
 * Jackson2 required
 * 
 * @author martin.vanek
 *
 */
public class CodeGeneratingHandler implements HttlResponseHandler {

	@Override
	public void onResponse(HttlRequest request, HttlResponse response) throws IOException {
		String mediaType = response.getMediaType();
		if (mediaType.indexOf("javascript") != -1 || mediaType.indexOf("json") != -1) {
			JsonInputGenerator generator = new JsonInputGenerator();
			String java = generator.process("com.example.httl.GeneratedResponse", response.getReader());
			System.out.println(java);
		} else if (mediaType.indexOf("xml") != -1) {
			throw new UnsupportedOperationException("Not yet " + mediaType);
		} else {
			throw new IllegalArgumentException("Unsupported media type " + mediaType);
		}
	}

	@Override
	public void onFailure(HttlRequest request, Exception exception) {
		exception.printStackTrace();
	}

}
