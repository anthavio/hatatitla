package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStreamReader;

import net.anthavio.httl.SenderResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;

/**
 * JSON -> Java 
 * Jackson 1 library based ResponseExtractor
 * 
 * httpSender.extract(new GetRequest("/my_entity.json"), new JsonResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class Jackson1ResponseExtractor<T> extends ResponseBodyExtractor<T> {

	//private final Class<T> resultType;

	private final JavaType javaType;

	private final ObjectMapper mapper;

	/**
	 * Externaly created ObjectMapper is provided
	
	public Jackson1ResponseExtractor(Class<T> resultType, ObjectMapper mapper) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		if (mapper == null) {
			throw new IllegalArgumentException("mapper is null");
		}
		this.mapper = mapper;

	}
	*/
	public Jackson1ResponseExtractor(JavaType javaType, ObjectMapper mapper) {
		if (javaType == null) {
			throw new IllegalArgumentException("javaType is null");
		}
		this.javaType = javaType;

		if (mapper == null) {
			throw new IllegalArgumentException("mapper is null");
		}
		this.mapper = mapper;
	}

	public ObjectMapper getObjectMapper() {
		return mapper;
	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			object = mapper.reader(javaType).readValue(new InputStreamReader(response.getStream(), response.getCharset()));
			return (T) object;
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + javaType + " value: " + object;
			throw new IllegalArgumentException(message, ccx);
		}
	}

	@Override
	public String toString() {
		return "Jackson1ResponseExtractor [resultType=" + javaType + ", mapper=" + mapper + "]";
	}

}