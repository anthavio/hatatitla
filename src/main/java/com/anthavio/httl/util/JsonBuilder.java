package com.anthavio.httl.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;

/**
 * Jackson (JSON weapon of choice) is missing simple JSON strings builder.
 * Other libraries are just too heavy for this quite simple job.
 * 
 * By default, builder use ISO8601 date format and does NOT perform formatting and indentation
 * 
 * Funny stuff with generics included!
 * 
 * @author martin.vanek
 *
 */
public class JsonBuilder {

	private boolean indenting;

	private int level = 0;

	private StringBuilder sb = new StringBuilder();

	private SimpleDateFormat dateFormat;

	/**
	 * Start JSON object { ...
	 * <br/>
	 * Shorthand to new JsonBuilder().object()
	 */
	public static ObjectBuilder<JsonBuilder> OBJECT() {
		return new JsonBuilder().object();
	}

	/**
	 * Start JSON array { ...
	 * <br/>
	 * Shorthand to new JsonBuilder().array()
	 */
	public static ArrayBuilder<JsonBuilder> ARRAY() {
		return new JsonBuilder().array();
	}

	public JsonBuilder(boolean indenting, String dateFormat) {
		this.indenting = indenting;
		if (dateFormat != null) {
			this.dateFormat = new SimpleDateFormat(dateFormat);
		}
	}

	public JsonBuilder(boolean indenting) {
		this(indenting, null);
	}

	public JsonBuilder(String dateFormat) {
		this(false, dateFormat);
		if (dateFormat == null) {
			throw new IllegalArgumentException("DateFormat pattern is null");
		}
	}

	public JsonBuilder() {
		this(false, null);
	}

	public boolean isIndenting() {
		return indenting;
	}

	public void setIndenting(boolean indenting) {
		this.indenting = indenting;
	}

	public void setDateFormat(String pattern) {
		this.dateFormat = new SimpleDateFormat(pattern);
	}

	/**
	 * Start Root JSON object
	 */
	public ObjectBuilder<JsonBuilder> object() {
		return new ObjectBuilder<JsonBuilder>(this);
	}

	/**
	 * Shorthand to start JSON object with field as simple value
	 */
	public ObjectBuilder<JsonBuilder> object(String fieldName, Object fieldValue) {
		return new ObjectBuilder<JsonBuilder>(this).field(fieldName, fieldValue);
	}

	/**
	 * Shorthand to start JSON object with field as nested object
	 */
	public ObjectBuilder<ObjectBuilder<JsonBuilder>> object(String fieldName) {
		return new ObjectBuilder<JsonBuilder>(this).object(fieldName);
	}

	/**
	 * Start Root JSON array
	 */
	public ArrayBuilder<JsonBuilder> array() {
		return new ArrayBuilder<JsonBuilder>(this);
	}

	/**
	 * Shorthand to create simple JSON array out of collection
	 */
	public JsonBuilder array(Collection<Object> collection) {
		value(collection);
		return this;
	}

	/**
	 * Shorthand to create simple JSON array out of array
	 */
	public JsonBuilder array(Object... elements) {
		value(elements);
		return this;
	}

	/**
	 * Shorthand to start JSON object with array field
	 */
	public ObjectBuilder<JsonBuilder> array(String name, Collection<Object> collection) {
		return new ObjectBuilder<JsonBuilder>(this).array(name, collection);
	}

	/**
	 * Shorthand to start JSON object with array filed
	 */
	public ObjectBuilder<JsonBuilder> array(String name, Object... elements) {
		return new ObjectBuilder<JsonBuilder>(this).array(name, elements);
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
				sb.append(value.toString()); //XXX is this enough ?
			} else if (value instanceof Date) {
				String dateTime;
				if (dateFormat != null) {
					dateTime = dateFormat.format((Date) value);
				} else {
					Calendar calendar = Calendar.getInstance();
					calendar.setTime((Date) value);
					dateTime = DatatypeConverter.printDateTime(calendar);
				}
				sb.append(dateTime);//ISO8601
			} else if (value instanceof Calendar) {
				String dateTime;
				if (dateFormat != null) {
					dateTime = dateFormat.format((Date) value);
				} else {
					dateTime = DatatypeConverter.printDateTime((Calendar) value);
				}
				sb.append(dateTime);//ISO8601
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
			++level;
			sb.append("{ ");
			if (indenting) {
				sb.append('\n');
				for (int i = 0; i < level; ++i) {
					sb.append("  ");
				}
			}
			this.parent = parent;
		}

		public T end() {
			--level;
			if (indenting) {
				sb.append('\n');
				for (int i = 0; i < level; ++i) {
					sb.append("  ");
				}
				sb.append("}");
			} else {
				sb.append(" }");
			}
			return parent;
		}

		private void name(String name) {
			if (name == null || name.length() == 0) {
				throw new IllegalArgumentException("field name is empty");
			}
			if (ff) {
				sb.append(", ");
				if (indenting) {
					sb.append('\n');
					for (int i = 0; i < level; ++i) {
						sb.append("  ");
					}
				}
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
		 * Field @param name as array
		 */
		public ObjectBuilder<T> field(String name, Object... value) {
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

		/**
		 * Shorthand for nested array with values
		 */
		public ObjectBuilder<T> array(String name, Collection<Object> collection) {
			name(name);
			value(collection);
			return this;
		}

		/**
		 * Shorthand for nested array with values
		 */
		public ObjectBuilder<T> array(String name, Object... elements) {
			name(name);
			value(elements);
			return this;
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

		/**
		 * Shorthand for nested array with values
		 */
		public ArrayBuilder<T> array(Collection<Object> collection) {
			element();
			value(collection);
			return this;
		}

		/**
		 * Shorthand for nested array with values
		 */
		public ArrayBuilder<T> array(Serializable... elements) {
			element();
			value(elements);
			return this;
		}
	}

	public static void main(String[] args) {
		JsonBuilder builder = new JsonBuilder(true).object("first", "value").object("second").field("yadda", "tadda").end()
				.field("third", 1, "x", null).end();

		System.out.println(builder.toString());
	}

}
