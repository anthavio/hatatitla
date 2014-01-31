package net.anthavio.httl.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * http://www.java-allandsundry.com/2012/12/json-deserialization-with-jackson-and.html
 * 
 * Enables nested generic types to be passed. Class is abstract so at least annonymous subclass must be created...
 * 
 * sender.GET("/broker/rest/api").extract(new GenericType<JoshiResponse<Map<String, ApiFunction>>>() {});
 * 
 * @author martin.vanek
 *
 */
public abstract class GenericType<T> {

	private final ParameterizedType type;

	public GenericType() {
		Class<?> parameterizedTypeReferenceSubclass = findParameterizedTypeReferenceSubclass(getClass());
		Type type = parameterizedTypeReferenceSubclass.getGenericSuperclass();
		if (!(type instanceof ParameterizedType)) {
			throw new IllegalArgumentException(type + " is not generic");
		}
		ParameterizedType parameterizedType = (ParameterizedType) type;
		if (parameterizedType.getActualTypeArguments().length != 1) {
			throw new IllegalArgumentException("Generic superclass must have single generic parameter instead of "
					+ parameterizedType.getActualTypeArguments().length);
		}
		type = parameterizedType.getActualTypeArguments()[0];
		if (!(type instanceof ParameterizedType)) {
			throw new IllegalArgumentException("Use me only for nested generic classes");
		}
		this.type = (ParameterizedType) type;
	}

	public ParameterizedType getParameterizedType() {
		return this.type;
	}

	@Override
	public boolean equals(Object obj) {
		return (this == obj || (obj instanceof GenericType && this.type.equals(((GenericType) obj).type)));
	}

	@Override
	public int hashCode() {
		return this.type.hashCode();
	}

	@Override
	public String toString() {
		return "ParameterizedTypeReference<" + this.type + ">";
	}

	private static Class<?> findParameterizedTypeReferenceSubclass(Class<?> child) {
		Class<?> parent = child.getSuperclass();
		if (Object.class.equals(parent)) {
			throw new IllegalStateException("Expected TypeReference superclass");
		} else if (GenericType.class.equals(parent)) {
			return child;
		} else {
			return findParameterizedTypeReferenceSubclass(parent);
		}
	}
}
