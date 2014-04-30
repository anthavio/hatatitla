package net.anthavio.httl.api;

import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.ApiTest.SomeBean;
import net.anthavio.httl.util.MockSender;

import org.fest.assertions.api.Assertions;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class BasicApiTest {

	@Test
	public void test() {
		MockSender sender = new MockSender();
		SimpleApi api = ApiBuilder.build(SimpleApi.class, sender);
		Assertions.assertThat(api.toString()).startsWith(
				"ProxyInvocationHandler for " + SimpleApi.class.getName() + " and HttpSender");
		Assertions.assertThat(api.equals(api)).isTrue();
		Assertions.assertThat(api.equals("z")).isFalse();

		api.returnVoid();
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoid");
		Assertions.assertThat(sender.getLastQuery()).isNull();
	}

	static interface SimpleApi {

		@Operation("GET /returnVoid")
		public void returnVoid();

		@Operation("GET /returnString")
		public String returnString();

		@Operation("GET /returnResponse")
		public SenderResponse returnResponse();

		@Operation("POST /returnVoidPostBean")
		public void returnVoidPostBean(@Body SomeBean bean);

		@Operation("POST /returnStringPostBean")
		public String returnStringPostBean(@Body SomeBean bean);

		@Operation("POST /returnResponsePostBean")
		public SenderResponse returnResponsePostBean(@Body SomeBean bean);

		@Operation("POST /returnVoidPostString")
		public void returnVoidPostString(@Body String string);

		@Operation("POST /returnVoidPostNothing")
		public void returnVoidPostNothing();
	}
}
