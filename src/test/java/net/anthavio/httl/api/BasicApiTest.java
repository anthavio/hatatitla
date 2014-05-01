package net.anthavio.httl.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;

import net.anthavio.httl.SenderBodyRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.ApiTest.SomeBean;
import net.anthavio.httl.util.HttpHeaderUtil;
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
	public void test() throws IOException {
		MockSender sender = new MockSender();
		String helloDolly = "Hello Dolly!";
		sender.setStaticResponse(201, "text/dolly", helloDolly);
		SimpleApi api = ApiBuilder.build(SimpleApi.class, sender);

		Assertions.assertThat(api.toString()).startsWith(
				"ProxyInvocationHandler for " + SimpleApi.class.getName() + " and HttpSender");
		Assertions.assertThat(api.equals(api)).isTrue();
		Assertions.assertThat(api.equals("zzz")).isFalse();

		//When
		api.returnVoid();
		//Then
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoid");
		Assertions.assertThat(sender.getLastQuery()).isNull();

		//When
		String returnString = api.returnString();
		//Then
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnString");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		Assertions.assertThat(returnString).isEqualTo(helloDolly);

		SenderResponse returnResponse = api.returnResponse();
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnResponse");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		Assertions.assertThat(returnResponse).isNotNull();
		Assertions.assertThat(returnResponse.getHttpStatusCode()).isEqualTo(201);
		Assertions.assertThat(returnResponse.getMediaType()).isEqualTo("text/dolly");
		Assertions.assertThat(returnResponse.getHeaders()).hasSize(1); //Content-Type
		Assertions.assertThat(HttpHeaderUtil.readAsString(returnResponse)).isEqualTo(helloDolly);
		Assertions.assertThat(new String(HttpHeaderUtil.readAsBytes(returnResponse))).isEqualTo(helloDolly);

		sender.setStaticResponse(null);

		api.returnVoidPostNothing();
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostNothing");

		//When
		SomeBean beanIn = new SomeBean("Quido", new Date(), 369);
		api.returnVoidPostBean(beanIn);

		//Then
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostBean");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		SenderBodyRequest bodyRequest = ((SenderBodyRequest) sender.getLastRequest());
		Assertions.assertThat(bodyRequest.getBodyStream()).isNotNull();

		//When
		api.returnVoidPostBean(null);
		//Then
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostBean");

		String returnStringPostBean = api.returnStringPostBean(beanIn);
		System.out.println(returnStringPostBean);

		String returnStringPostBean2 = api.returnStringPostBean(null);
		System.out.println(returnStringPostBean2);

		SenderResponse returnResponsePostBean = api.returnResponsePostBean(beanIn);

		String returnBeanPostBean = api.returnBeanPostBean(beanIn);

		api.returnVoidPostString(helloDolly);

		String returnStringPostString = api.returnStringPostString(helloDolly);

		String returnBeanPostString = api.returnBeanPostString(helloDolly);

		InputStream returnStreamPostBytes = api.returnStreamPostBytes(helloDolly.getBytes());

		Reader returnReaderPostString = api.returnReaderPostString(helloDolly);

		//api = ApiBuilder.build(SimpleApi.class, new HttpURLSender("http://google.com"));
		InputStream returnStreamPostStream = api.returnStreamPostStream(new ByteArrayInputStream(helloDolly.getBytes()));

		Reader returnReaderPostReader = api.returnReaderPostReader(new StringReader(helloDolly));

	}

	@Headers("Content-Type: application/json")
	static interface SimpleApi {

		@Operation("GET /returnVoid")
		public void returnVoid();

		@Operation("GET /returnString")
		public String returnString();

		@Operation("GET /returnResponse")
		public SenderResponse returnResponse();

		@Operation("POST /returnVoidPostNothing")
		public void returnVoidPostNothing();

		@Operation("POST /returnVoidPostBean")
		public void returnVoidPostBean(@Body SomeBean bean);

		@Operation("POST /returnStringPostBean")
		public String returnStringPostBean(@Body SomeBean bean);

		@Operation("POST /returnResponsePostBean")
		public SenderResponse returnResponsePostBean(@Body SomeBean bean);

		@Operation("POST /returnBeanPostBean")
		public String returnBeanPostBean(@Body SomeBean bean);

		@Operation("POST /returnVoidPostString")
		public void returnVoidPostString(@Body String string);

		@Operation("POST /returnStringPostString")
		public String returnStringPostString(@Body String string);

		@Operation("POST /returnBeanPostString")
		public String returnBeanPostString(@Body String string);

		@Operation("POST /returnStreamPostBytes")
		public InputStream returnStreamPostBytes(@Body byte[] bytes);

		@Operation("POST /returnReaderPostString")
		public Reader returnReaderPostString(@Body String string);

		@Operation("POST /returnStreamPostStream")
		public InputStream returnStreamPostStream(@Body InputStream stream);

		@Operation("POST /returnReaderPostReader")
		public Reader returnReaderPostReader(@Body Reader reader);

	}
}
