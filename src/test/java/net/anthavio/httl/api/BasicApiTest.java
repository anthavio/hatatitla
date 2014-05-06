package net.anthavio.httl.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;

import net.anthavio.httl.SenderBodyRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.ComplexApiTest.SomeBean;
import net.anthavio.httl.util.HttpHeaderUtil;
import net.anthavio.httl.util.MockSender;

import org.apache.commons.io.IOUtils;
import org.fest.assertions.api.Assertions;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class BasicApiTest {

	@Test
	public void testBasics() throws IOException {
		// Given
		MockSender sender = new MockSender();
		String helloPlain = "Hello Inčučuna!";
		sender.setStaticResponse(201, "text/dolly", helloPlain);
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
		Assertions.assertThat(returnString).isEqualTo(helloPlain);

		SenderResponse returnResponse = api.returnResponse();
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnResponse");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		Assertions.assertThat(returnResponse).isNotNull();
		Assertions.assertThat(returnResponse.getHttpStatusCode()).isEqualTo(201);
		Assertions.assertThat(returnResponse.getMediaType()).isEqualTo("text/dolly");
		Assertions.assertThat(returnResponse.getHeaders()).hasSize(1); //Content-Type
		Assertions.assertThat(HttpHeaderUtil.readAsString(returnResponse)).isEqualTo(helloPlain);
		Assertions.assertThat(new String(HttpHeaderUtil.readAsBytes(returnResponse), "utf-8")).isEqualTo(helloPlain);

		sender.setStaticResponse(null);

		api.returnVoidPostNothing();
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostNothing");

	}

	@Test
	public void testBeans() throws IOException {
		// Given
		MockSender sender = new MockSender();
		String helloPlain = "Hello Inčučuna!";
		//sender.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = ApiBuilder.build(SimpleApi.class, sender);

		final SomeBean beanIn = new SomeBean("Quido", new Date(), 369);
		final String jsonbean = sender.getRequestMarshaller("application/json").marshall(beanIn);
		//final String xmlbean = sender.getRequestMarshaller("application/xml").marshall(beanIn);

		//When
		api.returnVoidPostBean(beanIn);

		//Then
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostBean");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		SenderBodyRequest bodyRequest = ((SenderBodyRequest) sender.getLastRequest());
		Assertions.assertThat(bodyRequest.getBodyStream()).isNotNull();

		//When
		api.returnVoidPostBean(null);
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/returnVoidPostBean");

		//When
		String returnStringPostBean = api.returnStringPostBean(beanIn);
		Assertions.assertThat(returnStringPostBean).isEqualTo(jsonbean);

		//When
		String returnStringPostBeanNull = api.returnStringPostBean(null);
		Assertions.assertThat(returnStringPostBeanNull).startsWith("MockResponse");
		Assertions.assertThat(((SenderBodyRequest) sender.getLastRequest()).getBodyStream()).isNull();
		SenderResponse returnResponsePostBean = api.returnResponsePostBean(beanIn);
		Assertions.assertThat(HttpHeaderUtil.readAsString(returnResponsePostBean)).isEqualTo(jsonbean);

		SomeBean returnBeanPostBean = api.returnBeanPostBean(beanIn);
		Assertions.assertThat(returnBeanPostBean).isEqualsToByComparingFields(beanIn);

		api.returnVoidPostString(helloPlain);

		String returnStringPostString = api.returnStringPostString(helloPlain);
		Assertions.assertThat(returnStringPostString).isEqualTo(helloPlain);

		SomeBean returnBeanPostString = api.returnBeanPostString(jsonbean);
		Assertions.assertThat(returnBeanPostString).isEqualsToByComparingFields(beanIn);

	}

	@Test
	public void testStreams() throws IOException {
		// Given
		MockSender sender = new MockSender();
		String helloPlain = "Hello Inčučuna!";
		sender.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = ApiBuilder.build(SimpleApi.class, sender);

		InputStream returnStreamPostBytes = api.returnStreamPostBytes(helloPlain.getBytes("utf-8"));
		Assertions.assertThat(new String(IOUtils.toByteArray(returnStreamPostBytes), "utf-8")).isEqualTo(helloPlain);

		Reader returnReaderPostString = api.returnReaderPostString(helloPlain);
		Assertions.assertThat(IOUtils.toString(returnReaderPostString)).isEqualTo(helloPlain);

		InputStream returnStreamPostStream = api.returnStreamPostStream(new ByteArrayInputStream(helloPlain
				.getBytes("utf-8")));
		Assertions.assertThat(IOUtils.toString(returnStreamPostStream, "utf-8")).isEqualTo(helloPlain);

		Reader returnReaderPostReader = api.returnReaderPostReader(new StringReader(helloPlain));
		Assertions.assertThat(IOUtils.toString(returnReaderPostReader)).isEqualTo(helloPlain);
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
		public SomeBean returnBeanPostBean(@Body SomeBean bean);

		@Operation("POST /returnVoidPostString")
		public void returnVoidPostString(@Body String string);

		@Operation("POST /returnStringPostString")
		public String returnStringPostString(@Body String string);

		@Operation("POST /returnBeanPostString")
		public SomeBean returnBeanPostString(@Body String string);

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
