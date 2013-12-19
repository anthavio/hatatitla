package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStreamReader;

import net.anthavio.httl.SenderResponse;

import com.google.gson.Gson;

/**
 * JSON -> Java bean ResponseExtractor
 * 
 * https://code.google.com/p/google-gson/
 * 
 * @author martin.vanek
 *
 */
public class GsonResponseExtractor<T> extends ResponseBodyExtractor<T> {

	private final Class<T> resultType;

	private final Gson gson;

	/**
	 * Externaly created ObjectMapper is provided
	 */
	public GsonResponseExtractor(Class<T> resultType, Gson gson) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		if (gson == null) {
			throw new IllegalArgumentException("gson is null");
		}
		this.gson = gson;
	}

	public Gson getGson() {
		return gson;
	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			return gson.fromJson(new InputStreamReader(response.getStream(), response.getCharset()), resultType);
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + resultType.getName() + " value: "
					+ object;
			throw new IllegalArgumentException(message, ccx);
		}
	}

	@Override
	public String toString() {
		return "GsonResponseExtractor [resultType=" + resultType + ", gson=" + gson + "]";
	}

}