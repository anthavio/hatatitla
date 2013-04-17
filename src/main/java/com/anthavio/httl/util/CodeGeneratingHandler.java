package com.anthavio.httl.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.inout.ResponseHandler;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class CodeGeneratingHandler implements ResponseHandler {

	@Override
	public void onResponse(SenderResponse response) throws IOException {
		String mediaType = response.getMediaType();
		if (mediaType.indexOf("javascript") != -1 || mediaType.indexOf("json") != -1) {
			System.out.println("writing " + mediaType);
		} else if (mediaType.indexOf("xml") != -1) {
			System.out.println("writing " + mediaType);
		} else {
			throw new IllegalArgumentException("Unsupported media type " + mediaType);
		}
	}

	@Override
	public void onRequestError(SenderRequest request, Exception exception) {
		exception.printStackTrace();
	}

	@Override
	public void onResponseError(SenderResponse response, Exception exception) {
		exception.printStackTrace();
	}

	static class JavaCodeGenerator {

		protected final Logger logger = LoggerFactory.getLogger(getClass());

		protected Stack<AstNode> stack = new Stack<AstNode>();

		protected boolean doGlobalTypes = false;
		//finished custom objects - for global style declarations
		protected List<AstNode> globals = new ArrayList<AstNode>();

		protected Class<?> extend;

		protected List<Class<?>> implement = new ArrayList<Class<?>>();

		protected String targetPackage;

		protected String buildFieldName(String name) {
			char c0 = name.charAt(0);
			if (Character.isDigit(c0)) { //OK in JSON, NOT in Java
				name = "f" + name;
			} else if (Character.isUpperCase(c0)) {
				if (name.length() == 1) {
					name = Character.toLowerCase(c0) + "";
				} else {
					name = Character.toLowerCase(c0) + name.substring(1);
				}
			}
			return name;
		}

		protected String buildClassName(String name) {
			char c0 = name.charAt(0);
			if (Character.isDigit(c0)) {
				name = "C" + name;
			} else if (Character.isLowerCase(c0)) {
				if (name.length() == 1) {
					name = Character.toUpperCase(c0) + "";
				} else {
					name = Character.toUpperCase(c0) + name.substring(1);
				}
			}
			return name;
		}

		public void generate(AstNode root) {
			StringWriter sw = new StringWriter();
			IndentingWriter w = new IndentingWriter(sw);
			if (Cutils.isNotEmpty(targetPackage)) {
				w.println("package " + targetPackage + ";");
			}
			//pw.println("import java.util.List;");
			//pw.println("import java.util.Map;");
			//pw.println("import java.util.Date;");

			if (root.isArray()) {

				if (root.getTypeName().equals("java.util.Map")) {
					//heretogenous array 
					List<AstNode> elements = root.getElements();
					for (AstNode element : elements) {
						genClass(element, w);
					}
				} else {
					//homogenous array - just first
					genClass(root.getElements().get(0), w);
				}
			} else {
				genClass(root, w);
			}

			for (AstNode global : globals) {
				genClass(global, w);
			}

			System.out.println(sw);
		}

		private void genClass(AstNode node, IndentingWriter w) {
			w.print("public class ");
			w.print(node.getTypeName());
			if (extend != null) {
				w.print(" extends " + extend.getName());
			}
			if (implement.size() != 0) {
				w.print(" implements");
				for (int i = 0; i < implement.size(); ++i) {
					Class<?> itf = implement.get(i);
					w.print(itf.getName());
					if (i < implement.size() - 1) {
						w.print(", ");
					}
				}
			}
			w.print(" {");
			w.println();
			w.incLevel();

			List<AstNode> fields = node.getElements();
			for (AstNode field : fields) {
				genFieldDeclaration(w, field);
			}

			for (AstNode field : fields) {
				genFieldGetSet(w, field);
			}

			//local types
			for (AstNode local : node.locals) {
				genClass(local, w);
			}

			w.decLevel();
			w.println("}");
		}

		private void genFieldGetSet(IndentingWriter w, AstNode field) {
			String type = buildFieldType(field);
			String fieldName = buildFieldName(field.getName());
			String name;
			if (fieldName.length() == 1) {
				name = Character.toUpperCase(fieldName.charAt(0)) + "";
			} else {
				name = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
			}
			//getter
			w.print("public ");
			w.print(type);
			w.print(" get" + name + "() {");
			w.incLevel();
			w.println();
			w.println("return this." + fieldName + ";");
			w.decLevel();
			w.println("}");
			//setter
			w.print("public void ");
			w.print(" set" + name + "(" + type + " " + fieldName + ") {");
			w.incLevel();
			w.println();
			w.println("this." + fieldName + " = " + fieldName + ";");
			w.decLevel();
			w.println("}");

		}

		private void genFieldDeclaration(IndentingWriter w, AstNode field) {
			String type = buildFieldType(field);
			String name = buildFieldName(field.getName());
			w.print("private " + type + " " + name + ";");
			w.println();
		}

		private String buildFieldType(AstNode field) {
			StringBuilder sb = new StringBuilder();
			AstNode f = field;
			while (f.isArray()) {
				sb.append("java.util.List<");
				f = f.getElements().get(0); //JSON null?
				//sb.append(buildFieldType(field.getElements().get(0)));
			}

			sb.append(field.getTypeName());

			f = field;
			while (f.isArray()) {
				f = f.getElements().get(0); //JSON null?
				sb.append(">");
			}

			return sb.toString();
		}

	}

	public static class JsonInputGenerator extends JavaCodeGenerator {

		private final String rootName;

		private DateFormat dateFormat;

		public JsonInputGenerator(String rootName) {
			if (Cutils.isEmpty(rootName)) {
				throw new IllegalArgumentException("rootName is empty");
			}
			this.rootName = rootName;
		}

		public void process(Reader reader) throws IOException {
			AstNode root = parse(reader);
			System.out.println(root);
			generate(root);
		}

		private AstNode parse(Reader reader) throws IOException, JsonParseException {
			ObjectMapper mapper = new ObjectMapper();
			JsonFactory factory = mapper.getFactory();
			JsonParser parser = factory.createJsonParser(reader);
			String xName = rootName + System.currentTimeMillis();
			stack.add(new AstNode(xName, null, false)); //artificial root element - must stay on the top of the Stack
			doField(rootName, parser);
			if (!xName.equals(stack.peek().getName())) {
				throw new IllegalStateException("Artificial element " + xName + " not found on the top of the stack");
			}
			AstNode rootEntry = stack.pop().getElements().get(0);
			if (!rootEntry.getName().equals(rootName)) {
				throw new IllegalStateException("Root element " + rootName + " does not match with " + rootEntry.getName());
			}
			return rootEntry;
		}

		private void doField(String name, JsonParser parser) throws IOException {
			logger.debug("Field begin " + name);
			JsonToken token = parser.nextToken();
			if (token == JsonToken.START_OBJECT) {
				doObject(name, parser);
			} else if (token == JsonToken.START_ARRAY) {
				doArray(name, parser);
			} else { //must be value then
				Class<?> type = doValue(parser);
				stack.peek().addField(name, type);
			}
			logger.debug("Field ended " + name);
		}

		private AstNode doObject(String name, JsonParser parser) throws IOException {
			logger.debug("Object begin " + name);
			AstNode object = AstNode.object(name);
			stack.push(object);
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				JsonToken token = parser.getCurrentToken();
				if (token == JsonToken.FIELD_NAME) {
					String fieldName = parser.getCurrentName();
					doField(fieldName, parser);
				} else {
					throw new IllegalArgumentException("Unexpected token " + token);
				}
			}
			if (stack.pop() != object) {
				throw new IllegalStateException("Stack corrupted for " + object);
			}
			//System.out.println(object.getName() + " " + object.getTypeName());
			object = declare(object);

			stack.peek().addField(object); //add to parent
			logger.debug("Object ended " + name + " " + object);
			return object;
		}

		private AstNode declare(AstNode object) {
			logger.debug("declare " + object);
			List<AstNode> objects;
			if (doGlobalTypes) {
				objects = globals;
			} else { //!doGlobalTypes
				AstNode parent = stack.peek();
				int s = stack.size();
				while (parent.isArray()) {
					parent = stack.get(--s); //cannot declare in array
				}
				objects = parent.locals;
			}

			//if we already have object with same set of fields - use it instead of creating new
			for (AstNode existing : objects) {
				//System.out.println(object + " vs " + existing);
				if (existing.equalsFields(object)) {
					//System.out.println("existing " + object);
					return existing;
				}
			}

			//object with same set of fields not found - create new
			String typeName = buildClassName(object.getName());
			//check for name collision
			for (AstNode existing : objects) {
				if (existing.getName().equals(typeName)) {
					typeName = stack.peek().getName() + typeName; //use enclosing object name as prefix
				}
			}
			object.setTypeName(typeName);
			objects.add(object);

			return object;
		}

		private AstNode doArray(String name, JsonParser parser) throws IOException {
			logger.debug("Array begin " + name);
			AstNode array = AstNode.array(name);
			stack.push(array);
			int counter = 0;
			AstNode first = null;
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				++counter;
				JsonToken token = parser.getCurrentToken();
				//We don't know type name for array element - create artificial
				//String eName = array.getName();
				AstNode element;
				if (token == JsonToken.START_OBJECT) {
					element = doObject(name, parser);
				} else if (token == JsonToken.START_ARRAY) {
					element = doArray(name, parser);
				} else { //must be value then
					Class<?> type = doValue(parser);
					if (type == Void.class) {
						continue; //skip JSON null elements
					}
					element = array.addField(name, type);
				}

				if (first == null) {
					first = element;
					//System.out.println("array will be " + element.getTypeName());
					array.setTypeName(element.getTypeName());
				} else if (!first.equalsFields(element)) {
					if (first.isSimpleType() && element.isSimpleType()) {
						//if heterogenity is for example Integer vs String, we can still use to String...
						logger.debug("Heterogenous simple type array " + name + " of " + first.getTypeName() + " vs "
								+ element.getTypeName());
						array.setTypeName(String.class);
					} else {
						if ((first.isSimpleType() && !element.isSimpleType()) || (!first.isSimpleType() && element.isSimpleType())) {
							throw new IllegalStateException("Mixing simple and complex types in array is not allowed: "
									+ first.getTypeName() + " vs " + element.getTypeName());
						}
						logger.warn("Heterogenous complex type array " + name + " of " + first.getTypeName() + " vs "
								+ element.getTypeName());
						first.setTypeName(first.getTypeName() + "1");//distinguish class names
						element.setTypeName(element.getTypeName() + counter);
						array.setTypeName(Map.class); //Jackson can parse anything into Map
					}
				}
			}

			if (stack.pop() != array) {
				throw new IllegalStateException("Stack corrupted for " + array);
			}
			stack.peek().addField(array); //add this array into parent object
			logger.debug("Array ended " + name + " " + array);
			return array;
		}

		private Class<?> doValue(JsonParser parser) throws IOException {
			Class<?> type;
			JsonToken token = parser.getCurrentToken();
			String value = parser.getValueAsString();
			switch (token) {
			case VALUE_STRING:
				type = String.class;
				break;
			case VALUE_NUMBER_INT:
				type = Integer.class;
				break;
			case VALUE_NUMBER_FLOAT:
				type = Float.class;
				break;
			case VALUE_TRUE:
				type = Boolean.class;
				break;
			case VALUE_FALSE:
				type = Boolean.class;
				break;
			case VALUE_NULL:
				type = Void.class;
				break;
			default:
				throw new IllegalArgumentException("Unexpected token " + token);
			}
			if (type == String.class && dateFormat != null) {
				try {
					dateFormat.parse(value);
				} catch (ParseException px) {
					//just a string, not a date
				}
			}
			logger.debug("Value " + value + " of " + type);
			return type;
		}
	}

	/*
		class XmlInputGenerator extends JavaCodeGenerator {
			public XmlInputGenerator(Reader reader) {
				XMLInputFactory factory = XMLInputFactory.newFactory();
				XMLEventReader xmlReader = factory.createXMLEventReader(reader);
				xmlReader.nextEvent();
			}
		}
	*/

	static class AstNode {

		//localy declared types
		public List<AstNode> locals = new ArrayList<AstNode>();

		private final String name;

		private final List<AstNode> fielems = new ArrayList<AstNode>(); //fields for Class, elements for Array

		private String typeName;

		private final boolean array;

		private boolean simpleType;

		public static AstNode array(String name) {
			return new AstNode(name, null, true);
		}

		public static AstNode object(String name) {
			return new AstNode(name, null, false);
		}

		public static AstNode Fielem(String name, Class clazz) {
			return new AstNode(name, clazz, false);
		}

		private AstNode(String name, Class clazz, boolean array) {
			if (Cutils.isEmpty(name)) {
				throw new IllegalArgumentException("Name is empty");
			}
			this.name = name;

			if (clazz != null) {
				setTypeName(clazz);
			}

			this.array = array;
		}

		public String getName() {
			return name;
		}

		public String getTypeName() {
			return typeName;
		}

		public boolean isSimpleType() {
			return simpleType;
		}

		//For Array
		public void setTypeName(Class clazz) {
			String name = clazz.getName();
			if (name.startsWith("java.lang")) {
				this.typeName = clazz.getSimpleName();
				this.simpleType = true;
			} else {
				this.typeName = clazz.getName();
				this.simpleType = false;
			}
		}

		//For Object (custom)
		public void setTypeName(String name) {
			this.typeName = name;
		}

		//Fields (Object) or Elements (Array)
		public List<AstNode> getElements() {
			return fielems;
		}

		public AstNode addField(String name, Class type) {
			if (Cutils.isEmpty(name)) {
				throw new IllegalArgumentException("Name is empty");
			}
			AstNode node = new AstNode(name, type, false);
			fielems.add(node);
			return node;
		}

		public void addField(AstNode entry) {
			fielems.add(entry);
		}

		public boolean isArray() {
			return array;
		}

		@Override
		public String toString() {
			return "{name=" + name + ", typeName=" + typeName + ", list=" + array + ", fields=" + fielems + "}";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fielems == null) ? 0 : fielems.hashCode());
			result = prime * result + (array ? 1231 : 1237);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((typeName == null) ? 0 : typeName.hashCode());
			return result;
		}

		public boolean equalsFields(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AstNode other = (AstNode) obj;
			if (array != other.array)
				return false;
			if (fielems == null) {
				if (other.fielems != null)
					return false;
			} else if (!array) {
				if (simpleType) { //when simple - type must be same
					if (typeName == null) {
						if (other.typeName != null)
							return false;
					} else if (!typeName.equals(other.typeName))
						return false;
				} else { //when class - field must be precisely the same
					if (!fielems.equals(other.fielems))
						return false;
				}
			} else if (array) { //when array - only type of fields must by same
				//we will determine field's type by own type
				if (typeName == null) {
					if (other.typeName != null)
						return false;
				} else if (!typeName.equals(other.typeName))
					return false;
			}

			/*
			if (typeName == null) {
				if (other.typeName != null)
					return false;
			} else if (!typeName.equals(other.typeName))
				return false;
			*/
			return true;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AstNode other = (AstNode) obj;
			if (fielems == null) {
				if (other.fielems != null)
					return false;
			} else if (!fielems.equals(other.fielems))
				return false;
			if (array != other.array)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (typeName == null) {
				if (other.typeName != null)
					return false;
			} else if (!typeName.equals(other.typeName))
				return false;
			return true;
		}
	}

	public static class Clazz {

		private final Class<?> clazz;

		private final String name;

		/**
		 * Custom class
		 */
		public Clazz(String name) {
			this.name = name;
			this.clazz = null;
		}

		/**
		 * String/Integer/Date
		 */
		public Clazz(Class<?> clazz) {
			if (clazz == null) {
				throw new IllegalArgumentException("null clazz");
			}
			this.clazz = clazz;
			this.name = null;
		}

		public String getName() {
			if (clazz != null) {
				String name = clazz.getName();
				if (name.startsWith("java.lang")) {
					return clazz.getSimpleName();
				} else {
					return clazz.getName();
				}
			} else {
				return name;
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Clazz other = (Clazz) obj;
			if (clazz == null) {
				if (other.clazz != null)
					return false;
			} else if (!clazz.equals(other.clazz))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Clazz [clazz=" + clazz + ", name=" + name + "]";
		}

	}

}

class IndentingWriter {

	private String token = "\t";

	private int level = 0;

	private final PrintWriter pw;

	private boolean newline;

	public IndentingWriter(OutputStream stream) {
		this.pw = new PrintWriter(stream);
	}

	public IndentingWriter(Writer writer) {
		this.pw = new PrintWriter(writer);
	}

	public int incLevel() {
		return ++level;
	}

	public int decLevel() {
		return --level;
	}

	public void print(Object object) {
		if (newline) {
			for (int i = 0; i < level; ++i) {
				pw.print(token);
			}
			newline = false;
		}
		pw.print(object);
	}

	public void println() {
		pw.println();
		this.newline = true;
	}

	public void println(Object object) {
		print(object);
		pw.println();
		this.newline = true;
	}

	public void close() {
		pw.close();
	}

}
