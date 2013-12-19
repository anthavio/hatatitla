package net.anthavio.httl;

import java.io.Serializable;
import java.util.List;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.ElementList;

/**
 * 
 * @author martin.vanek
 *
 */
//@XmlRootElement(name = "response")
//@Default(DefaultType.PROPERTY)
@Default(DefaultType.FIELD)
public class TestResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private String message;

	private Request request;

	protected TestResponse() {
		//jaxb
	}

	public TestResponse(String message, Request request) {
		this.message = message;
		this.request = request;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Request getRequest() {
		return request;
	}

	public void setRequest(Request request) {
		this.request = request;
	}

	@Default(DefaultType.FIELD)
	public static class Request {

		//@Element
		private String message;

		@ElementList(entry = "headers", inline = true)
		private List<NameValue> headers;

		@ElementList(entry = "parameters", inline = true)
		private List<NameValue> parameters;

		public List<NameValue> getHeaders() {
			return headers;
		}

		public void setHeaders(List<NameValue> headers) {
			this.headers = headers;
		}

		public List<NameValue> getParameters() {
			return parameters;
		}

		public void setParameters(List<NameValue> paramaters) {
			this.parameters = paramaters;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

	public static class NameValue {

		private String name;

		private String value;

		protected NameValue() {
			//jaxb
		}

		public NameValue(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	@Override
	public String toString() {
		return "TestResponse [message=" + message + ", request=" + request + "]";
	}

}
