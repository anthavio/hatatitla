package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlProcessingException;
import net.anthavio.httl.HttlStatusException;
import net.anthavio.httl.HttlBodyUnmarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * Storage for ResponseUnmarshaller
 * 
 * @author martin.vanek
 *
 */
public class Unmarshallers implements HttlBodyUnmarshaller {

	private Map<String, List<HttlBodyUnmarshaller>> unmarshallers = new HashMap<String, List<HttlBodyUnmarshaller>>();

	//String and byte[] unmarshaller is special because they are not media type dependent

	private HttlBodyUnmarshaller stringUnmarshaller = new StringUnmarshaller();

	private HttlBodyUnmarshaller bytesUnmarshaller = new BytesUnmarshaller();

	public HttlBodyUnmarshaller getStringUnmarshaller() {
		return stringUnmarshaller;
	}

	public void setStringUnmarshaller(HttlBodyUnmarshaller stringUnmarshaller) {
		if (stringUnmarshaller == null) {
			throw new IllegalArgumentException("Null unmarshaller");
		}
		this.stringUnmarshaller = stringUnmarshaller;
	}

	public HttlBodyUnmarshaller getBytesUnmarshaller() {
		return bytesUnmarshaller;
	}

	public void setBytesUnmarshaller(HttlBodyUnmarshaller bytesUnmarshaller) {
		if (bytesUnmarshaller == null) {
			throw new IllegalArgumentException("Null unmarshaller");
		}
		this.bytesUnmarshaller = bytesUnmarshaller;
	}

	@Override
	public HttlBodyUnmarshaller supports(HttlResponse response, Type returnType) {
		// Special Unmarshaller first
		if (returnType.equals(String.class)) {
			return stringUnmarshaller;
		} else if (returnType.equals(byte[].class)) {
			return bytesUnmarshaller;
		}
		// Scan by mediaType
		String mediaType = response.getMediaType();
		if (mediaType != null) {
			List<HttlBodyUnmarshaller> list = unmarshallers.get(mediaType);

			//Init default
			if (list == null) {
				list = new ArrayList<HttlBodyUnmarshaller>();
				unmarshallers.put(mediaType, list);
				HttlBodyUnmarshaller defaultx = Defaults.getDefaultUnmarshaller(mediaType);
				if (defaultx != null) {
					list.add(defaultx);
				}
			}

			for (HttlBodyUnmarshaller item : list) {
				if (item.supports(response, returnType) != null) {
					return item;
				}
			}
		}
		return null;
	}

	@Override
	public Object unmarshall(HttlResponse response, Type returnType) throws IOException {
		HttlBodyUnmarshaller unmarshaller = supports(response, returnType);
		if (unmarshaller != null) {
			return unmarshaller.unmarshall(response, returnType);
		} else {
			throw new HttlProcessingException("No Unmarshaller for response: " + response + " return type: " + returnType);
		}
	}

	/**
	 * Add into first position
	 */
	public void addUnmarshaller(HttlBodyUnmarshaller unmarshaller, String mediaType) {
		getUnmarshallers(mediaType).add(0, unmarshaller);
	}

	/**
	 * Returned list is open for changes...
	 */
	public List<HttlBodyUnmarshaller> getUnmarshallers(String mediaType) {
		List<HttlBodyUnmarshaller> list = unmarshallers.get(mediaType);
		if (list == null) {
			list = new ArrayList<HttlBodyUnmarshaller>();
			unmarshallers.put(mediaType, list);
		}
		return list;
	}

	public static class StringUnmarshaller implements HttlBodyUnmarshaller {

		@Override
		public HttlBodyUnmarshaller supports(HttlResponse response, Type returnType) {
			return returnType == String.class ? this : null;
		}

		@Override
		public String unmarshall(HttlResponse response, Type returnType) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsString(response);
			} else {
				throw new HttlStatusException(response);
			}
		}

	};

	public static class BytesUnmarshaller implements HttlBodyUnmarshaller {

		@Override
		public HttlBodyUnmarshaller supports(HttlResponse response, Type returnType) {
			return returnType == byte[].class ? this : null;
		}

		@Override
		public byte[] unmarshall(HttlResponse response, Type returnType) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsBytes(response);
			} else {
				throw new HttlStatusException(response);
			}
		}

	};

	public static class ReaderUnmarshaller implements HttlBodyUnmarshaller {

		@Override
		public HttlBodyUnmarshaller supports(HttlResponse response, Type returnType) {
			return returnType == Reader.class ? this : null;
		}

		@Override
		public Reader unmarshall(HttlResponse response, Type returnType) throws IOException {
			return response.getReader();
		}

	};

	public static class StreamUnmarshaller implements HttlBodyUnmarshaller {

		@Override
		public HttlBodyUnmarshaller supports(HttlResponse response, Type returnType) {
			return returnType == InputStream.class ? this : null;
		}

		@Override
		public InputStream unmarshall(HttlResponse response, Type returnType) throws IOException {
			return response.getStream();
		}

	};

}
