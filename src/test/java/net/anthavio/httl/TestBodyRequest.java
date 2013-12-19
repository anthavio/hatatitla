package net.anthavio.httl;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * @author martin.vanek
 *
 */
@XmlRootElement(name = "request")
public class TestBodyRequest {

	private String message;

	protected TestBodyRequest() {
		//jaxb
	}

	public TestBodyRequest(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
