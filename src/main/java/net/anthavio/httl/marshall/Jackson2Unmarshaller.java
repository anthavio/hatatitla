package net.anthavio.httl.marshall;

import java.io.IOException;
import java.lang.reflect.Type;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlBodyUnmarshaller.ConfigurableUnmarshaller;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON -> Java 
 * Jackson 2 based Unmarshaller
 * 
 * httpSender.extract(new GetRequest("/my_entity.json"), new JsonResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class Jackson2Unmarshaller extends ConfigurableUnmarshaller {

	private final ObjectMapper mapper;

	public Jackson2Unmarshaller() {
		this(Jackson2Util.build());
	}

	public Jackson2Unmarshaller(ObjectMapper mapper) {
		this(mapper, "application/json");
	}

	public Jackson2Unmarshaller(ObjectMapper mapper, String mediaType) {
		this(mapper, mediaType, 200, 299);
	}

	public Jackson2Unmarshaller(ObjectMapper mapper, String mediaType, int minHttpStatus, int maxHttpStatus) {
		super(mediaType, minHttpStatus, maxHttpStatus);
		if (mapper == null) {
			throw new IllegalArgumentException("ObjectMapper is null");
		}
		this.mapper = mapper;
	}

	public ObjectMapper getObjectMapper() {
		return mapper;
	}

	@Override
	public Object unmarshall(HttlResponse response, Type resultType) throws IOException {
		JavaType javaType = mapper.constructType(resultType); //TODO cache JavaType
		return mapper.reader(javaType).readValue(response.getReader());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [ mapper=" + mapper + "]";
	}

}