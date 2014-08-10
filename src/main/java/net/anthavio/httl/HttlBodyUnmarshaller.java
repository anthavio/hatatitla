package net.anthavio.httl;

import java.io.IOException;
import java.lang.reflect.Type;

import net.anthavio.httl.util.GenericType;

/**
 * Work like Factory (supports) and Unmarshaller (unmarshall) in the same time
 * 
 * @author martin.vanek
 *
 */
public interface HttlBodyUnmarshaller {

	public abstract Object unmarshall(HttlResponse response, Type returnType) throws IOException;

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
			/*
			if (minHttpCode < 100 || minHttpCode > 599) {
				throw new IllegalArgumentException("Http code out of range <100,599>: " + minHttpCode);
			}
			*/
			this.minHttpCode = minHttpCode;
			/*
			if (maxHttpCode < 100 || maxHttpCode > 599) {
				throw new IllegalArgumentException("Http code out of range <100,599>: " + maxHttpCode);
			}
			*/
			this.maxHttpCode = maxHttpCode;

			if (minHttpCode > maxHttpCode) {
				throw new IllegalArgumentException("Invalid range: " + minHttpCode + " > " + maxHttpCode);
			}

			if (mediaType == null || mediaType.isEmpty()) {
				throw new IllegalArgumentException("Media type is null or empty");
			}
			this.mediaType = mediaType;

		}

		public HttlBodyUnmarshaller supports(HttlResponse response, Type resultType) {
			boolean isRangeOk = response.getHttpStatusCode() >= minHttpCode && response.getHttpStatusCode() <= maxHttpCode;
			boolean isMediaOk = mediaType.equals(ANY_MEDIA_TYPE) || mediaType.equals(response.getMediaType());
			return isRangeOk && isMediaOk ? this : null;
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
