package net.anthavio.httl.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class JsonInputGenerator extends JavaCodeGenerator {

	public JsonInputGenerator() {

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

	public AstNode parse(String className, Reader reader) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonFactory factory = mapper.getFactory();
		JsonParser parser = factory.createJsonParser(reader);
		String xName = className + System.currentTimeMillis();
		stack.add(new AstNode(xName, (Class<?>) null, false)); //artificial root element - must stay on the top of the Stack
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
		logger.debug(">> Field " + name);
		JsonToken token = parser.nextToken();
		String typeName;
		if (token == JsonToken.START_OBJECT) {
			AstNode node = doObject(name, parser);
			typeName = node.getTypeName();
		} else if (token == JsonToken.START_ARRAY) {
			AstNode node = doArray(name, parser);
			typeName = node.getTypeName();
		} else { //must be value then
			Class<?> type = doValue(parser);
			typeName = type.getName();
			stack.peek().addField(name, type);
		}
		logger.debug("<< Field " + name + " type " + typeName);
	}

	private AstNode doObject(String name, JsonParser parser) throws IOException {
		logger.debug(">> Object " + name);
		AstNode object = new AstNode(name, (Class<?>) null, false);
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
		logger.debug("<< Object " + name + " " + object);
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
				object = new AstNode(object.getName(), existing.getTypeName(), existing.isArray());
				return object;
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
		logger.debug(">> Array " + name);
		AstNode array = new AstNode(name, (Class<?>) null, true);
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

		if (array.getElements().isEmpty()) {
			array.setTypeName(Void.class); //empty array
		}

		logger.debug("<< Array " + name + " " + array);
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
		logger.debug("== value '" + value + "' type " + type.getName());
		return type;
	}

}
/*
	public static class XmlInputGenerator extends JavaCodeGenerator {
		
		@Override
		public AstNode parse(String className, Reader reader) throws IOException {
			XMLInputFactory factory = XMLInputFactory.newFactory();
			XMLEventReader xmlReader = factory.createXMLEventReader(reader);
			String xName = className + System.currentTimeMillis();
			stack.add(new AstNode(xName, null, false)); //artificial root element - must stay on the top of the Stack
			doField(className, xmlReader);
			if (!xName.equals(stack.peek().getName())) {
				throw new IllegalStateException("Artificial element " + xName + " not found on the top of the stack");
			}
			AstNode rootEntry = stack.pop().getElements().get(0);
			if (!rootEntry.getName().equals(className)) {
				throw new IllegalStateException("Root element " + className + " does not match with " + rootEntry.getName());
			}
			return rootEntry;
		}

		private void doField(String name, XMLEventReader xmlReader) throws IOException {
			logger.debug("Field begin " + name);
			XMLEvent token = xmlReader.nextEvent();
			if (token.getEventType() == XMLEvent.START_ELEMENT) {
				doObject(name, xmlReader);
			} else if (token == JsonToken.START_ARRAY) {
				doArray(name, xmlReader);
			} else { //must be value then
				Class<?> type = doValue(xmlReader);
				stack.peek().addField(name, type);
			}
			logger.debug("Field ended " + name);
		}

		private Class<?> doValue(XMLEventReader xmlReader) throws IOException {
			Class<?> type;
			XMLEvent token = xmlReader.peek();
			String value = xmlReader.getElementText();
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

		private AstNode doObject(String name, XMLEventReader xmlReader) throws IOException {
			logger.debug("Object begin " + name);
			AstNode object = AstNode.object(name);
			stack.push(object);
			while (xmlReader.nextToken() != JsonToken.END_OBJECT) {
				JsonToken token = xmlReader.peek();
				if (token == JsonToken.FIELD_NAME) {
					String fieldName = xmlReader.getCurrentName();
					doField(fieldName, xmlReader);
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
	}
*/

