package net.anthavio.httl.marshall;

import java.io.IOException;
import java.lang.reflect.Type;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlUnmarshaller.ConfigurableUnmarshaller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * JSON -> Java bean Unmarshaller
 * 
 * https://code.google.com/p/google-gson/
 * 
 * @author martin.vanek
 *
 */
public class GsonUnmarshaller extends ConfigurableUnmarshaller {

	private final Gson gson;

	public GsonUnmarshaller() {
		// Java7 - "yyyy-MM-dd'T'HH:mm:ss.SSSX"
		this(new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssz").create());
	}

	/**
	 * Externaly created ObjectMapper is provided
	 */
	public GsonUnmarshaller(Gson gson) {
		this(gson, "application/json");
	}

	public GsonUnmarshaller(Gson gson, String mediaType) {
		this(gson, mediaType, 200, 299);
	}

	public GsonUnmarshaller(Gson gson, String mediaType, int minHttpStatus, int maxHttpStatus) {
		super(mediaType, minHttpStatus, maxHttpStatus);
		if (gson == null) {
			throw new IllegalArgumentException("Gson is null");
		}
		this.gson = gson;
	}

	public Gson getGson() {
		return gson;
	}

	@Override
	public Object unmarshall(HttlResponse response, Type resultType) throws IOException {
		return gson.fromJson(response.getReader(), resultType);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [gson=" + gson + "]";
	}

}