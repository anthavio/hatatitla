package com.anthavio.httl.util;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * 
 * @author martin.vanek
 *
 */
public class SimpleJsonBuilder {

	private StringBuilder sb = new StringBuilder();

	/**
	 * Start Root JSON object
	 */
	public ObjectBuilder<SimpleJsonBuilder> object() {
		return new ObjectBuilder<SimpleJsonBuilder>(this);
	}

	/**
	 * Start Root JSON array
	 */
	public ArrayBuilder<SimpleJsonBuilder> array() {
		return new ArrayBuilder<SimpleJsonBuilder>(this);
	}

	private void value(Object value) {
		if (value == null) {
			sb.append("null");
		} else {
			if (value instanceof String) {
				sb.append('"').append(value).append('"');
			} else if (value instanceof Boolean) {
				sb.append(value.toString());
			} else if (value instanceof Number) {
				sb.append(value.toString());
			} else if (value instanceof Collection<?>) {
				Collection<?> collection = (Collection<?>) value;
				sb.append("[ ");
				boolean ff = false;
				for (Object element : collection) {
					if (ff) {
						sb.append(", ");
					} else {
						ff = true;
					}
					value(element); //recursive
				}
				sb.append(" ]");
			} else if (value.getClass().isArray()) {
				int length = Array.getLength(value);
				sb.append("[ ");
				for (int i = 0; i < length; i++) {
					if (i != 0) {
						sb.append(", ");
					}
					Object element = Array.get(value, i);
					value(element); //recursive
				}
				sb.append(" ]");
			} else {
				throw new IllegalArgumentException("Unsuported type " + value.getClass().getName() + " of the value " + value);
			}
		}
	}

	public String getJson() {
		return sb.toString();
	}

	@Override
	public String toString() {
		return getJson();
	}

	public class ObjectBuilder<T> {

		private final T parent;

		private boolean ff; //first field flag

		private ObjectBuilder(T parent) {
			sb.append("{ ");
			this.parent = parent;
		}

		public T end() {
			sb.append(" }");
			return parent;
		}

		private void name(String name) {
			if (name == null || name.length() == 0) {
				throw new IllegalArgumentException("field name is empty");
			}
			if (ff) {
				sb.append(", ");
			} else {
				ff = true;
			}
			sb.append('"').append(name).append('"');
			sb.append(" : ");
		}

		/**
		 * Field @param name as simple value
		 */
		public ObjectBuilder<T> field(String name, Object value) {
			name(name);
			value(value);
			return this;
		}

		/**
		 * Field @param name as nested JSON object
		 */
		public ObjectBuilder<ObjectBuilder<T>> object(String name) {
			name(name);
			return new ObjectBuilder<ObjectBuilder<T>>(this);
		}

		/**
		 * Field @param name as nested JSON array
		 */
		public ArrayBuilder<ObjectBuilder<T>> array(String name) {
			name(name);
			return new ArrayBuilder<ObjectBuilder<T>>(this);
		}

	}

	public class ArrayBuilder<T> {

		private final T parent;

		private boolean ff; //first element flag

		private ArrayBuilder(T parent) {
			sb.append('[');
			this.parent = parent;
		}

		public T end() {
			sb.append(']');
			return parent;
		}

		private void element() {
			if (ff) {
				sb.append(", ");
			} else {
				ff = true;
			}
		}

		/**
		 * Simple value array element
		 */
		public ArrayBuilder<T> element(Object value) {
			element();
			value(value);
			return this;
		}

		/**
		 * Nested object array element
		 */
		public ObjectBuilder<ArrayBuilder<T>> object() {
			element();
			return new ObjectBuilder<ArrayBuilder<T>>(this);
		}

		/**
		 * Nested array array element
		 */
		public ArrayBuilder<ArrayBuilder<T>> array() {
			element();
			return new ArrayBuilder<ArrayBuilder<T>>(this);
		}

	}

	public static void main(String[] args) {
		String json = new SimpleJsonBuilder().object().field("f1", "hello").array("f2").element(1).element("2").element(3)
				.end().object("f3").field("n", 5.5).end().end().getJson();
		System.out.println(json);
		/*
				JsonObjectBuilder builder = Json.createObjectBuilder();
				JsonObject jsonObject = builder.add("x", "y").add("z", builder).build();
				System.out.println(jsonObject);

				JsonGenerator jg = javax.json.Json.createGenerator(System.out);
				jg.writeStartObject().write("whatever").writeEnd();
		*/
	}

}
