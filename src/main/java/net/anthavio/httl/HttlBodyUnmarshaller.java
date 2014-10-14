package net.anthavio.httl;

import java.io.IOException;
import java.lang.reflect.Type;

import net.anthavio.httl.util.GenericType;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyUnmarshaller {

	/**
	 * Unmarshall response.getStream()
	 */
	public Object unmarshall(HttlResponse response, Type returnType) throws IOException;

	/**
	 * Configurable multipurpose Unmarshaller
	 */
	public static abstract class ConfigurableUnmarshaller implements HttlBodyUnmarshaller {

		public static final String ANY_MEDIA_TYPE = "*/*";

		protected final int minHttpCode;

		protected final int maxHttpCode;

		protected final String mediaType;

		public ConfigurableUnmarshaller(String mediaType) {
			this(mediaType, 200, 299);
		}

		public ConfigurableUnmarshaller(String mediaType, int httpCode) {
			this(mediaType, httpCode, httpCode);
		}

		public ConfigurableUnmarshaller(String mediaType, int minHttpCode, int maxHttpCode) {
			this.minHttpCode = minHttpCode;
			this.maxHttpCode = maxHttpCode;

			if (minHttpCode > maxHttpCode) {
				throw new IllegalArgumentException("Invalid range: " + minHttpCode + " > " + maxHttpCode);
			}

			if (mediaType == null || mediaType.isEmpty()) {
				throw new IllegalArgumentException("Media type is null or empty");
			}
			this.mediaType = mediaType;

		}

		public abstract Object doUnmarshall(HttlResponse response, Type returnType) throws IOException;

		public Object unmarshall(HttlResponse response, Type returnType) throws IOException {
			int status = response.getHttpStatusCode();
			if (status > maxHttpCode || status < minHttpCode) {
				throw new HttlResponseException(response, "Http status " + status + " is outside of range <" + minHttpCode
						+ "," + maxHttpCode + ">");
			}
			if (!mediaType.equals(response.getMediaType()) && !mediaType.equals(ANY_MEDIA_TYPE)) {
				throw new HttlResponseException(response, "Mime type: " + response.getMediaType() + " does not match: "
						+ mediaType);
			}
			return doUnmarshall(response, returnType);
		}

		public <T> T unmarshall(HttlResponse response, Class<T> resultType) throws IOException {
			return (T) unmarshall(response, (Type) resultType);
		}

		public <T> T unmarshall(HttlResponse response, GenericType<T> resultType) throws IOException {
			return (T) unmarshall(response, resultType.getParameterizedType());
		}

		public String getMediaType() {
			return mediaType;
		}

		public int getMinHttpCode() {
			return minHttpCode;
		}

		public int getMaxHttpCode() {
			return maxHttpCode;
		}

	}

}
