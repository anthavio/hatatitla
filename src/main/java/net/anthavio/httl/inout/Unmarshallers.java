package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.ResponseExtractor;
import net.anthavio.httl.ResponseStatusException;
import net.anthavio.httl.ResponseUnmarshaller;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * Storage for ResponseUnmarshaller
 * 
 * @author martin.vanek
 *
 */
public class Unmarshallers {

	private Map<String, List<ResponseUnmarshaller>> unmarshallers = new HashMap<String, List<ResponseUnmarshaller>>();

	//String and byte[] unmarshaller is special because it does not case about media type

	private ResponseUnmarshaller stringUnmarshaller = STRING_UNMAR;

	private ResponseUnmarshaller bytesUnmarshaller = BYTES_UNMAR;

	public ResponseUnmarshaller getStringUnmarshaller() {
		return stringUnmarshaller;
	}

	public void setStringUnmarshaller(ResponseUnmarshaller stringUnmarshaller) {
		if (stringUnmarshaller == null) {
			throw new IllegalArgumentException("Null unmarshaller");
		}
		this.stringUnmarshaller = stringUnmarshaller;
	}

	public ResponseUnmarshaller getBytesUnmarshaller() {
		return bytesUnmarshaller;
	}

	public void setBytesUnmarshaller(ResponseUnmarshaller bytesUnmarshaller) {
		if (bytesUnmarshaller == null) {
			throw new IllegalArgumentException("Null unmarshaller");
		}
		this.bytesUnmarshaller = bytesUnmarshaller;
	}

	public ResponseUnmarshaller findUnmarshaller(HttlResponse response, Type returntype) {
		// Special Unmars first
		if (returntype.equals(String.class)) {
			return stringUnmarshaller;
		} else if (returntype.equals(byte[].class)) {
			return bytesUnmarshaller;
		}
		// Scan by mediaType
		String mediaType = response.getMediaType();
		if (mediaType != null) {
			List<ResponseUnmarshaller> list = unmarshallers.get(mediaType);

			//Init default
			if (list == null) {
				list = new ArrayList<ResponseUnmarshaller>();
				unmarshallers.put(mediaType, list);
				ResponseUnmarshaller defaultu = Defaults.getDefaultUnmarshaller(mediaType);
				if (defaultu != null) {
					list.add(defaultu);
				}
			}

			for (ResponseUnmarshaller extractor : list) {
				if (extractor.support(response, returntype)) {
					return extractor;
				}
			}
		}
		return null;
	}

	/**
	 * Add into first position
	 */
	public void addUnmarshaller(ResponseUnmarshaller unmarshaller, String mediaType) {
		getUnmarshallers(mediaType).add(0, unmarshaller);
	}

	/**
	 * Returned list is open for changes...
	 */
	public List<ResponseUnmarshaller> getUnmarshallers(String mediaType) {
		List<ResponseUnmarshaller> list = unmarshallers.get(mediaType);
		if (list == null) {
			list = new ArrayList<ResponseUnmarshaller>();
			unmarshallers.put(mediaType, list);
		}
		return list;
	}

	public static final ResponseUnmarshaller STRING_UNMAR = new ResponseUnmarshaller() {

		@Override
		public boolean support(HttlResponse response, Type returnType) {
			return returnType == String.class;
		}

		@Override
		public String unmarshall(HttlResponse response, Type returnType) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsString(response);
			} else {
				throw new ResponseStatusException(response);
			}
		}

	};

	public static final ResponseUnmarshaller BYTES_UNMAR = new ResponseUnmarshaller() {

		@Override
		public boolean support(HttlResponse response, Type returnType) {
			return returnType == byte[].class;
		}

		@Override
		public byte[] unmarshall(HttlResponse response, Type returnType) throws IOException {
			if (response.getHttpStatusCode() >= 200 && response.getHttpStatusCode() <= 299) {
				return HttpHeaderUtil.readAsBytes(response);
			} else {
				throw new ResponseStatusException(response);
			}
		}

	};

	public static final ResponseUnmarshaller READER_U = new ResponseUnmarshaller() {

		@Override
		public boolean support(HttlResponse response, Type returnType) {
			return returnType == Reader.class;
		}

		@Override
		public Reader unmarshall(HttlResponse response, Type returnType) throws IOException {
			return response.getReader();
		}

	};

	public static final ResponseExtractor<Reader> READER = new ResponseExtractor<Reader>() {

		@Override
		public boolean support(HttlResponse response) {
			return true;
		}

		@Override
		public Reader extract(HttlResponse response) throws IOException {
			return response.getReader();
		}
	};

	public static final ResponseUnmarshaller STREAM_U = new ResponseUnmarshaller() {

		@Override
		public boolean support(HttlResponse response, Type returnType) {
			return returnType == InputStream.class;
		}

		@Override
		public InputStream unmarshall(HttlResponse response, Type returnType) throws IOException {
			return response.getStream();
		}

	};

	public static final ResponseExtractor<InputStream> STREAM = new ResponseExtractor<InputStream>() {

		@Override
		public boolean support(HttlResponse response) {
			return true;
		}

		@Override
		public InputStream extract(HttlResponse response) throws IOException {
			return response.getStream();
		}
	};

}
