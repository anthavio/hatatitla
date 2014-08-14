package net.anthavio.httl.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;

import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.AdvancedApiTest.TestBodyBean;
import net.anthavio.httl.marshall.Jackson2Marshaller;
import net.anthavio.httl.util.HttpHeaderUtil;
import net.anthavio.httl.util.MockSenderConfig;
import net.anthavio.httl.util.MockTransport;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
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
				"ApiInvocationHandler for " + SimpleApi.class.getName() + " and HttlSender");
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

		api.void2void();
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/void2void");
	}

	@RestHeaders("Content-Type: application/json")
	static interface SimpleApi {

		@RestCall("GET /returnVoid")
		public void returnVoid();

		@RestCall("GET /returnString")
		public String returnString();

		@RestCall("GET /returnResponse")
		public HttlResponse returnResponse();

		@RestCall("POST /void2void")
		public void void2void();

	}

	@Test
	public void testBeans() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		Jackson2Marshaller jrm = new Jackson2Marshaller(new ObjectMapper().configure(
				SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true));
		HttlSender sender = new MockSenderConfig(transport).setMarshaller(jrm).build();

		String helloPlain = "Hello Inčučuna!";
		//sender.setStaticResponse(201, "text/dolly", helloPlain);
		ApiWithBeans api = HttlApiBuilder.build(ApiWithBeans.class, sender);

		final TestBodyBean beanIn = new TestBodyBean("Kvído Vymětal", new Date(), 369);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		jrm.write(beanIn, baos, "utf-8");
		final String jsonbean = new String(baos.toByteArray(), "utf-8");

		//final String xmlbean = sender.getRequestMarshaller("application/xml").marshall(beanIn);

		//When
		api.bean2void(beanIn);

		//Then
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/bean2void");
		Assertions.assertThat(transport.getLastRequest().getBody()).isNotNull();

		//When
		api.bean2void(null);
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/bean2void");

		//When
		String bean2string = api.bean2string(beanIn);
		Assertions.assertThat(bean2string).isEqualTo(jsonbean);

		//When
		bean2string = api.bean2string(null);
		Assertions.assertThat(bean2string).startsWith("MockResponse");
		Assertions.assertThat(transport.getLastRequest().getBody()).isNull();

		HttlResponse bean2response = api.bean2response(beanIn);
		Assertions.assertThat(HttpHeaderUtil.readAsString(bean2response)).isEqualTo(jsonbean);

		TestBodyBean bean2bean = api.bean2bean(beanIn);
		Assertions.assertThat(bean2bean).isEqualToComparingFieldByField(beanIn);

		api.string2void(helloPlain);

		String string2string = api.string2string(helloPlain);
		Assertions.assertThat(string2string).isEqualTo(helloPlain);

		TestBodyBean string2bean = api.string2bean(jsonbean);
		Assertions.assertThat(string2bean).isEqualToComparingFieldByField(beanIn);

	}

	@RestHeaders("Content-Type: application/json")
	static interface ApiWithBeans {

		@RestCall("POST /bean2void")
		public void bean2void(@RestBody TestBodyBean bean);

		@RestCall("POST /bean2string")
		public String bean2string(@RestBody TestBodyBean bean);

		@RestCall("POST /bean2response")
		public HttlResponse bean2response(@RestBody TestBodyBean bean);

		@RestCall("POST /bean2bean")
		public TestBodyBean bean2bean(@RestBody TestBodyBean bean);

		@RestCall("POST /string2void")
		public void string2void(@RestBody String string);

		@RestCall("POST /string2string")
		public String string2string(@RestBody String string);

		@RestCall("POST /string2bean")
		public TestBodyBean string2bean(@RestBody String string);
	}

	@Test
	public void testStreams() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();

		String helloPlain = "Hello Inčučuna!";
		transport.setStaticResponse(201, "text/dolly", helloPlain);
		ApiWithStreams api = HttlApiBuilder.build(ApiWithStreams.class, sender);

		//When
		InputStream postBytesReturnStream = api.postBytesReturnStream(helloPlain.getBytes("utf-8"));
		//Then
		Assertions.assertThat(new String(IOUtils.toByteArray(postBytesReturnStream), "utf-8")).isEqualTo(helloPlain);
		postBytesReturnStream.close();

		//When
		Reader postStringReturnReader = api.postStringReturnReader(helloPlain);
		//Then
		Assertions.assertThat(IOUtils.toString(postStringReturnReader)).isEqualTo(helloPlain);
		postStringReturnReader.close();

		//When
		InputStream postStreamReturnStream = api.postStreamReturnStream(new ByteArrayInputStream(helloPlain
				.getBytes("utf-8")));
		//Then
		Assertions.assertThat(IOUtils.toString(postStreamReturnStream, "utf-8")).isEqualTo(helloPlain);
		postStreamReturnStream.close();

		//When
		Reader postReaderReturnReader = api.postReaderReturnReader(new StringReader(helloPlain));
		//Then
		Assertions.assertThat(IOUtils.toString(postReaderReturnReader)).isEqualTo(helloPlain);
		postReaderReturnReader.close();
	}

	@RestHeaders("Content-Type: application/json")
	static interface ApiWithStreams {

		@RestCall("POST /returnStreamPostBytes")
		public InputStream postBytesReturnStream(@RestBody byte[] bytes);

		@RestCall("POST /returnReaderPostString")
		public Reader postStringReturnReader(@RestBody String string);

		@RestCall("POST /returnStreamPostStream")
		public InputStream postStreamReturnStream(@RestBody InputStream stream);

		@RestCall("POST /returnReaderPostReader")
		public Reader postReaderReturnReader(@RestBody Reader reader);
	}

	@Test
	public void emptyParamaterApis() {
		HttlSender sender = HttlSender.For("www.example.com").build();
		try {
			HttlApiBuilder.with(sender).build(WrongApiMissingName.class);
			Assertions.fail("Expected " + HttlApiException.class.getSimpleName());
		} catch (HttlApiException abx) {
			Assertions.assertThat(abx.getMessage()).startsWith("Missing parameter's name on position 1");
		}

		try {
			HttlApiBuilder.with(sender).build(WrongApiEmptyName.class);
			Assertions.fail("Expected " + HttlApiException.class.getSimpleName());
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

	@Test
	public void wrongGetWithBody() {
		HttlSender sender = HttlSender.For("www.example.com").build();
		try {
			HttlApiBuilder.with(sender).build(WrongApiGetWithBody.class);
			Assertions.fail("Expected " + HttlApiException.class.getSimpleName());
		} catch (HttlApiException abx) {
			Assertions.assertThat(abx.getMessage()).startsWith("Body not allowed");
		}
	}

	static interface WrongApiGetWithBody {

		@RestCall("GET /whatever")
		public void whatever(@RestBody String body); //GET with body is nonsense

	}
}
