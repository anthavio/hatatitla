package net.anthavio.httl;

import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.Field;

import net.anthavio.httl.util.JsonBuilder;
import net.anthavio.httl.util.JsonInputGenerator;


/**
 * 
 * @author martin.vanek
 *
 */
public class GeneratorsTest {

	public static void main(String[] args) {

		try {
			new GeneratorsTest().test();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public void test() throws Exception {

		JsonInputGenerator generator = new JsonInputGenerator();
		generator.setBaseClass(Object.class);
		generator.addInterface(Serializable.class);
		generator.setDateFormat("yyyy.MM.dd");

		//String string = "{ \"f\" : { \"m\": 1.1, \"n\": 2.2 }, \"x\" : [ { \"x\" : \"a\"} , {\"x\" : \"c\"} ] }";
		String string = JsonBuilder.OBJECT()//
				.object("f").field("m", 1.1).field("n", "2012.12.05").end()//
				.array("x")//
				.object().field("x", "a").end()//
				.object().field("x", "c").end()//
				.end()//
				.end()//
				.getJson();
		System.out.println(string);

		//String string = "[ {\"f\" : { \"m\": 1.1, \"n\": \"2012.11.05\" }, \"x\" : [ { \"x\" : \"a\"} , {\"x\" : \"c\"} ]} ]";
		//String string = "{ \"f\" :[ null , [2, \"x\" , \"y\"] , [ \"1\" , \"a\" , 2 ] ]}";
		//String string = " [ {\"x\" : \"z\" } , { \"x\" : \"y\"} ]";

		String className = "com.example.FromJson";
		String javaCode = generator.process(className, new StringReader(string));
		System.out.println(javaCode);
		generator.compile(className, javaCode);

		Class<?> class1 = Class.forName(className);
		Field[] fields = class1.getDeclaredFields();
		for (Field field : fields) {
			System.out.println(field.getType() + " " + field.getName());
		}

	}
}
