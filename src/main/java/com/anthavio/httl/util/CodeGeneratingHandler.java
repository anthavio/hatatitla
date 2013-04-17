package com.anthavio.httl.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

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

		//declare classes as global or keep them inner (local)
		protected boolean doGlobalTypes = false;

		//finished custom objects - for global style declarations
		protected List<AstNode> globals = new ArrayList<AstNode>();

		//base class to extend
		protected String baseClass;

		//interfaces to implement
		protected List<String> interfaces;

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

		private String buildFieldType(AstNode field) {
			StringBuilder sb = new StringBuilder();
			AstNode f = field;
			while (f.isArray()) {
				sb.append("java.util.List<");
				f = f.getElements().get(0); //JSON null?
			}

			sb.append(field.getTypeName());

			f = field;
			while (f.isArray()) {
				f = f.getElements().get(0); //JSON null?
				sb.append(">");
			}

			return sb.toString();
		}

		public void write(AstNode root, Writer writer) {

			IndentingWriter w = new IndentingWriter(writer);
			if (Cutils.isNotEmpty(targetPackage)) {
				w.println("package " + targetPackage + ";");
			}
			//pw.println("import java.util.List;");
			//pw.println("import java.util.Map;");
			//pw.println("import java.util.Date;");
			w.println("/**");
			w.println(" * Generated by Hatatitla at " + new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()));
			w.println(" */");
			if (root.isArray()) {

				if (root.getTypeName().equals("java.util.Map")) {
					//heretogenous array - elements has different types
					List<AstNode> elements = root.getElements();
					for (AstNode element : elements) {
						writeClass(element, Modifier.PUBLIC, w);
					}
				} else {
					//homogenous array - type is same so just first
					writeClass(root.getElements().get(0), Modifier.PUBLIC, w);
				}
			} else {
				writeClass(root, Modifier.PUBLIC, w);
			}

			for (AstNode global : globals) {
				writeClass(global, 0, w);
			}

		}

		private void writeClass(AstNode node, int modifiers, IndentingWriter w) {
			if (Modifier.isPublic(modifiers)) {
				w.print("public ");
			} else if (Modifier.isProtected(modifiers)) {
				w.print("protected ");
			} else if (Modifier.isProtected(modifiers)) {
				w.print("private ");
			}
			if (Modifier.isStatic(modifiers)) {
				w.print("static ");
			}
			if (Modifier.isFinal(modifiers)) {
				w.print("final ");
			}
			w.print("class " + node.getTypeName());

			if (Cutils.isNotEmpty(baseClass)) {
				w.print(" extends " + baseClass);
			}
			if (interfaces.size() != 0) {
				w.print(" implements ");
				for (int i = 0; i < interfaces.size(); ++i) {
					String itf = interfaces.get(i);
					w.print(itf);
					if (i < interfaces.size() - 1) {
						w.print(", ");
					}
				}
			}
			w.print(" {");
			w.println();
			w.incLevel();

			List<AstNode> fields = node.getElements();
			for (AstNode field : fields) {
				writeFieldDeclaration(w, field);
			}

			for (AstNode field : fields) {
				writeFieldGetSet(w, field);
			}

			//local types
			for (AstNode local : node.locals) {
				writeClass(local, Modifier.PUBLIC | Modifier.STATIC, w);
			}

			w.decLevel();
			w.println("}");
		}

		private void writeFieldGetSet(IndentingWriter w, AstNode field) {
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

		private void writeFieldDeclaration(IndentingWriter w, AstNode field) {
			String type = buildFieldType(field);
			String name = buildFieldName(field.getName());
			w.print("private " + type + " " + name + ";");
			w.println();
		}

		public boolean isDoGlobalTypes() {
			return doGlobalTypes;
		}

		public void setDoGlobalTypes(boolean doGlobalTypes) {
			this.doGlobalTypes = doGlobalTypes;
		}

		public String getBaseClass() {
			return baseClass;
		}

		public void setBaseClass(Class<?> clazz) {
			this.baseClass = clazz.getName();
		}

		public void setBaseClass(String className) {
			this.baseClass = className;
		}

		public List<String> getInterfaces() {
			return interfaces;
		}

		public void setInterfaces(List<Class<?>> interfaces) {
			this.interfaces = new ArrayList<String>();
			for (Class<?> clazz : interfaces) {
				this.interfaces.add(clazz.getName());
			}
		}

		public void addInterface(Class<?> interfacex) {
			addInterface(interfacex.getName());
		}

		public void addInterface(String interfaceName) {
			if (this.interfaces == null) {
				this.interfaces = new ArrayList<String>();
			}
			//disallow duplicities
			if (this.interfaces.indexOf(interfaceName) == -1) {
				this.interfaces.add(interfaceName);
			}
		}

		public String getTargetPackage() {
			return targetPackage;
		}

		public void setTargetPackage(String targetPackage) {
			this.targetPackage = targetPackage;
		}

	}

	public static class JsonInputGenerator extends JavaCodeGenerator {

		private DateFormat dateFormat;

		public JsonInputGenerator() {

		}

		public DateFormat getDateFormat() {
			return dateFormat;
		}

		public void setDateFormat(DateFormat dateFormat) {
			this.dateFormat = dateFormat;
		}

		public void setDateFormat(String pattern) {
			this.dateFormat = new SimpleDateFormat(pattern);

		}

		/**
		 * @param className if qualified, then package par will be aslo used
		 * @param reader source of JSON
		 * @return java code
		 */
		public String process(String className, Reader reader) throws IOException {
			if (Cutils.isEmpty(className)) {
				throw new IllegalArgumentException("rootName is empty");
			}
			int dotIdx = className.lastIndexOf('.');
			if (dotIdx != -1) {
				targetPackage = className.substring(0, dotIdx);
				className = className.substring(dotIdx + 1);
			}
			AstNode root = parse(className, reader);
			//System.out.println(root);
			StringWriter sw = new StringWriter();
			write(root, sw);
			return sw.toString();
		}

		public void compile(String className, String javaCode) throws IOException {
			String fileName;
			int dotIdx = className.lastIndexOf('.');
			if (dotIdx != -1) {
				fileName = className.substring(dotIdx + 1) + ".java";
			} else {
				fileName = className;
			}
			if (!fileName.endsWith(".java")) {
				fileName += ".java";
			}
			logger.debug("Compiling " + fileName);
			//javax.tool compiler api is cool as hell
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			DiagnosticCollector<JavaFileObject> diagnosticListener = new DiagnosticCollector<JavaFileObject>();
			StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticListener, Locale.getDefault(),
					Charset.forName("utf-8"));
			JavaFileObject java = new StringJavaObject(fileName, javaCode);
			List<JavaFileObject> compilationUnits = Arrays.asList(java);

			//otherwise classes are written to current directory
			String[] options = new String[] { "-d", "target/classes" };
			List<String> compileOptions = Arrays.asList(options);

			CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, compileOptions, null,
					compilationUnits);
			Boolean result = task.call();
			/*
			List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticListener.getDiagnostics();
			for (Diagnostic<? extends JavaFileObject> diagnosticItem : diagnostics) {
				System.out.format("Error in %s", diagnosticItem);
			}
			*/
			fileManager.close();

			if (!result) {
				throw new IllegalArgumentException("Compilation failed " + diagnosticListener.getDiagnostics());
			}

		}

		private AstNode parse(String className, Reader reader) throws IOException, JsonParseException {
			ObjectMapper mapper = new ObjectMapper();
			JsonFactory factory = mapper.getFactory();
			JsonParser parser = factory.createJsonParser(reader);
			String xName = className + System.currentTimeMillis();
			stack.add(new AstNode(xName, null, false)); //artificial root element - must stay on the top of the Stack
			doField(className, parser);
			if (!xName.equals(stack.peek().getName())) {
				throw new IllegalStateException("Artificial element " + xName + " not found on the top of the stack");
			}
			AstNode rootEntry = stack.pop().getElements().get(0);
			if (!rootEntry.getName().equals(className)) {
				throw new IllegalStateException("Root element " + className + " does not match with " + rootEntry.getName());
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

		/**
		 * XXX maybe provide some value parser / type resolver for pluggable types instead of this hardcoded stuff
		 */
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
					type = Date.class;
				} catch (ParseException px) {
					//not a date, keep it string
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

}

class AstNode {

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

	public static AstNode Fielem(String name, Class<?> clazz) {
		return new AstNode(name, clazz, false);
	}

	public AstNode(String name, Class<?> clazz, boolean array) {
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
	public void setTypeName(Class<?> clazz) {
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

	public AstNode addField(String name, Class<?> type) {
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

class Clazz {

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

class StringJavaObject extends SimpleJavaFileObject {
	private String contents = null;

	public StringJavaObject(String className, String classCode) {
		super(URI.create(className), Kind.SOURCE);
		this.contents = classCode;
	}

	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		return contents;
	}
}
