package com.anthavio.httl;

import java.io.StringReader;

import com.anthavio.httl.util.CodeGeneratingHandler.JsonInputGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class GeneratorMain {

	public static void main(String[] args) {
		JsonInputGenerator generator = new JsonInputGenerator("Rooter");
		//String string = "{ \"f\" : { \"m\": 1.1, \"n\": 2.2 }, \"x\" : [ { \"x\" : \"a\"} , {\"x\" : \"c\"} ] }";
		String string = "[ {\"f\" : { \"m\": 1.1, \"n\": 2.2 }, \"x\" : [ { \"x\" : \"a\"} , {\"x\" : \"c\"} ]} ]";
		//String string = "{ \"f\" :[ null , [2, \"x\" , \"y\"] , [ \"1\" , \"a\" , 2 ] ]}";
		//String string = " { \"f\" : [ 2, \"x\" , \"y\"] }";
		ObjectMapper mapper = new ObjectMapper();

		try {
			//mapper.readValue(string, Rooter.class);
			generator.process(new StringReader(string));
		} catch (Exception x) {
			x.printStackTrace();
		}
	}
}

class Rooter {
	private java.util.List<java.util.List<String>> f;

	public java.util.List<java.util.List<String>> getF() {
		return this.f;
	}

	public void setF(java.util.List<java.util.List<String>> f) {
		this.f = f;
	}
}