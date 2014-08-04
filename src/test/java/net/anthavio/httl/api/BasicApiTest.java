package net.anthavio.httl.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.ComplexApiTest.SomeBodyBean;
import net.anthavio.httl.marshall.Jackson2Marshaller;
import net.anthavio.httl.marshall.Marshallers;
import net.anthavio.httl.util.HttpHeaderUtil;
import net.anthavio.httl.util.MockSenderConfig;
import net.anthavio.httl.util.MockTransport;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 
 * @author martin.vanek
 *
 */
public class BasicApiTest {

	@Test
	public void testBasics() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();
		String helloPlain = "Hello Inčučuna!";
		transport.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = HttlApiBuilder.build(SimpleApi.class, sender);

		Assertions.assertThat(api.toString()).startsWith(
				"ApiInvocationHandler for " + SimpleApi.class.getName() + " and HttpSender");
		Assertions.assertThat(api.equals(api)).isTrue();
		Assertions.assertThat(api.equals("zzz")).isFalse();

		//When
		api.returnVoid();
		//Then
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnVoid");

		//When
		String returnString = api.returnString();
		//Then
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnString");
		Assertions.assertThat(returnString).isEqualTo(helloPlain);

		HttlResponse returnResponse = api.returnResponse();
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnResponse");
		Assertions.assertThat(returnResponse).isNotNull();
		Assertions.assertThat(returnResponse.getHttpStatusCode()).isEqualTo(201);
		Assertions.assertThat(returnResponse.getMediaType()).isEqualTo("text/dolly");
		Assertions.assertThat(returnResponse.getHeaders()).hasSize(1); //Content-Type
		Assertions.assertThat(HttpHeaderUtil.readAsString(returnResponse)).isEqualTo(helloPlain);
		Assertions.assertThat(new String(HttpHeaderUtil.readAsBytes(returnResponse), "utf-8")).isEqualTo(helloPlain);

		transport.setStaticResponse(null);

		api.returnVoidPostNothing();
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnVoidPostNothing");

	}

	@Test
	public void testBeans() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();
		Jackson2Marshaller jrm = (Jackson2Marshaller) sender.getConfig().getRequestMarshaller("application/json");
		jrm.getObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true); // millisecond precision

		String helloPlain = "Hello Inčučuna!";
		//sender.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = HttlApiBuilder.build(SimpleApi.class, sender);

		final SomeBodyBean beanIn = new SomeBodyBean("Kvído Vymětal", new Date(), 369);
		final String jsonbean = Marshallers.marshall(jrm, beanIn);

		//final String xmlbean = sender.getRequestMarshaller("application/xml").marshall(beanIn);

		//When
		api.returnVoidPostBean(beanIn);

		//Then
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnVoidPostBean");
		Assertions.assertThat(transport.getLastRequest().getBody()).isNotNull();

		//When
		api.returnVoidPostBean(null);
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/returnVoidPostBean");

		//When
		String returnStringPostBean = api.returnStringPostBean(beanIn);
		Assertions.assertThat(returnStringPostBean).isEqualTo(jsonbean);

		//When
		String returnStringPostBeanNull = api.returnStringPostBean(null);
		Assertions.assertThat(returnStringPostBeanNull).startsWith("MockResponse");
		Assertions.assertThat(transport.getLastRequest().getBody()).isNull();
		HttlResponse returnResponsePostBean = api.returnResponsePostBean(beanIn);
		Assertions.assertThat(HttpHeaderUtil.readAsString(returnResponsePostBean)).isEqualTo(jsonbean);

		SomeBodyBean returnBeanPostBean = api.returnBeanPostBean(beanIn);
		Assertions.assertThat(returnBeanPostBean).isEqualToComparingFieldByField(beanIn);

		api.returnVoidPostString(helloPlain);

		String returnStringPostString = api.returnStringPostString(helloPlain);
		Assertions.assertThat(returnStringPostString).isEqualTo(helloPlain);

		SomeBodyBean returnBeanPostString = api.returnBeanPostString(jsonbean);
		Assertions.assertThat(returnBeanPostString).isEqualToComparingFieldByField(beanIn);

	}

	@Test
	public void testStreams() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();

		String helloPlain = "Hello Inčučuna!";
		transport.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = HttlApiBuilder.build(SimpleApi.class, sender);

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

	@Test
	public void wrongsApis() {
		try {
			HttlApiBuilder.with(HttlSender.Build("www.example.com")).build(WrongApiMissingName.class);
			Assertions.fail("Previous statement must throw " + HttlApiException.class.getSimpleName());
		} catch (HttlApiException abx) {
			Assertions.assertThat(abx.getMessage()).startsWith("Missing parameter's name on position 1");
		}

		try {
			HttlApiBuilder.with(HttlSender.Build("www.example.com")).build(WrongApiEmptyName.class);
			Assertions.fail("Previous statement must throw " + HttlApiException.class.getSimpleName());
		} catch (HttlApiException abx) {
			Assertions.assertThat(abx.getMessage()).startsWith("Missing parameter's name on position 1");
		}

	}

	static interface WrongApiMissingName {

		@RestCall("GET /missing")
		public void missing(@RestVar String some);

	}

	static interface WrongApiEmptyName {

		@RestCall("GET /empty")
		public void missing(@RestVar("") String some);
	}

	@RestHeaders("Content-Type: application/json")
	static interface SimpleApi {

		@RestCall("GET /returnVoid")
		public void returnVoid();

		@RestCall("GET /returnString")
		public String returnString();

		@RestCall("GET /returnResponse")
		public HttlResponse returnResponse();

		@RestCall("POST /returnVoidPostNothing")
		public void returnVoidPostNothing();

		@RestCall("POST /returnVoidPostBean")
		public void returnVoidPostBean(@RestBody SomeBodyBean bean);

		@RestCall("POST /returnStringPostBean")
		public String returnStringPostBean(@RestBody SomeBodyBean bean);

		@RestCall("POST /returnResponsePostBean")
		public HttlResponse returnResponsePostBean(@RestBody SomeBodyBean bean);

		@RestCall("POST /returnBeanPostBean")
		public SomeBodyBean returnBeanPostBean(@RestBody SomeBodyBean bean);

		@RestCall("POST /returnVoidPostString")
		public void returnVoidPostString(@RestBody String string);

		@RestCall("POST /returnStringPostString")
		public String returnStringPostString(@RestBody String string);

		@RestCall("POST /returnBeanPostString")
		public SomeBodyBean returnBeanPostString(@RestBody String string);

		@RestCall("POST /returnStreamPostBytes")
		public InputStream returnStreamPostBytes(@RestBody byte[] bytes);

		@RestCall("POST /returnReaderPostString")
		public Reader returnReaderPostString(@RestBody String string);

		@RestCall("POST /returnStreamPostStream")
		public InputStream returnStreamPostStream(@RestBody InputStream stream);

		@RestCall("POST /returnReaderPostReader")
		public Reader returnReaderPostReader(@RestBody Reader reader);

	}
}
