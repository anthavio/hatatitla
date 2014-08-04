package net.anthavio.httl.marshall;

import java.io.IOException;
import java.lang.reflect.Type;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlUnmarshaller.ConfigurableUnmarshaller;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;

/**
 * JSON -> Java 
 * Jackson 1 library based Unmarshaller
 * 
 * httpSender.extract(new GetRequest("/my_entity.json"), new JsonResponseExtractor(MyEntity.class));
 * 
 * @author martin.vanek
 *
 */
public class Jackson1Unmarshaller extends ConfigurableUnmarshaller {

	private final ObjectMapper mapper;

	public Jackson1Unmarshaller() {
		this(Jackson1Util.build());
	}

	public Jackson1Unmarshaller(ObjectMapper mapper) {
		this(mapper, "application/json");
	}

	public Jackson1Unmarshaller(ObjectMapper mapper, String mediaType) {
		this(mapper, mediaType, 200, 299);
	}

	public Jackson1Unmarshaller(ObjectMapper mapper, String mediaType, int minHttpStatus, int maxHttpStatus) {
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
		return getClass().getSimpleName() + " [mapper=" + mapper + "]";
	}

}