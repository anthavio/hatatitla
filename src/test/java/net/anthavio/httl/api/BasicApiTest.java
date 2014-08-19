package net.anthavio.httl.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.AdvancedApiTest.TestBodyBean;
import net.anthavio.httl.marshall.Jackson2Marshaller;
import net.anthavio.httl.util.HttpHeaderUtil;
import net.anthavio.httl.util.MockTransConfig;
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
		HttlSender sender = new MockTransConfig(transport).sender().build();
		String helloPlain = "Hello Inčučuna!";
		transport.setStaticResponse(201, "text/dolly", helloPlain);
		SimpleApi api = HttlApiBuilder.build(SimpleApi.class, sender);

		Assertions.assertThat(api.toString()).startsWith(
				"ApiInvocationHandler for " + SimpleApi.class.getName() + " and HttlSender");
		Assertions.assertThat(api.equals(api)).isTrue();
		Assertions.assertThat(api.equals("zzz")).isFalse();

		//When
		api.void2void();
		//Then
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/void2void");

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

	}

	@HttlHeaders("Content-Type: application/json")
	static interface SimpleApi {

		@HttlCall("GET /returnString")
		public String returnString();

		@HttlCall("GET /returnResponse")
		public HttlResponse returnResponse();

		@HttlCall("POST /void2void")
		public void void2void();

	}

	@Test
	public void testBeans() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		Jackson2Marshaller jrm = new Jackson2Marshaller(new ObjectMapper().configure(
				SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true));
		HttlSender sender = new MockTransConfig(transport).sender().setMarshaller(jrm).build();

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

	@HttlHeaders("Content-Type: application/json")
	static interface ApiWithBeans {

		@HttlCall("POST /bean2void")
		public void bean2void(@HttlBody TestBodyBean bean);

		@HttlCall("POST /bean2string")
		public String bean2string(@HttlBody TestBodyBean bean);

		@HttlCall("POST /bean2response")
		public HttlResponse bean2response(@HttlBody TestBodyBean bean);

		@HttlCall("POST /bean2bean")
		public TestBodyBean bean2bean(@HttlBody TestBodyBean bean);

		@HttlCall("POST /string2void")
		public void string2void(@HttlBody String string);

		@HttlCall("POST /string2string")
		public String string2string(@HttlBody String string);

		@HttlCall("POST /string2bean")
		public TestBodyBean string2bean(@HttlBody String string);
	}

	@Test
	public void testStreams() throws IOException {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockTransConfig(transport).sender().build();

		String helloPlain = "Hello Inčučuna!";
		transport.setStaticResponse(201, "text/dolly", helloPlain);
		ApiWithStreams api = HttlApiBuilder.build(ApiWithStreams.class, sender);

		//When
		InputStream bytes2stream = api.bytes2stream(helloPlain.getBytes("utf-8"));
		//Then
		Assertions.assertThat(new String(IOUtils.toByteArray(bytes2stream), "utf-8")).isEqualTo(helloPlain);
		bytes2stream.close();

		//When
		Reader string2reader = api.string2reader(helloPlain);
		//Then
		Assertions.assertThat(IOUtils.toString(string2reader)).isEqualTo(helloPlain);
		string2reader.close();

		//When
		InputStream stream2stream = api.stream2stream(new ByteArrayInputStream(helloPlain.getBytes("utf-8")));
		//Then
		Assertions.assertThat(IOUtils.toString(stream2stream, "utf-8")).isEqualTo(helloPlain);
		stream2stream.close();

		//When
		Reader reader2reader = api.reader2reader(new StringReader(helloPlain));
		//Then
		Assertions.assertThat(IOUtils.toString(reader2reader)).isEqualTo(helloPlain);
		reader2reader.close();
	}

	@HttlHeaders("Content-Type: application/json")
	static interface ApiWithStreams {

		@HttlCall("POST /bytes2stream")
		public InputStream bytes2stream(@HttlBody byte[] bytes);

		@HttlCall("POST /string2reader")
		public Reader string2reader(@HttlBody String string);

		@HttlCall("POST /stream2stream")
		public InputStream stream2stream(@HttlBody InputStream stream);

		@HttlCall("POST /reader2reader")
		public Reader reader2reader(@HttlBody Reader reader);
	}

	/**
	 * Map is prominent structure with special handling
	 */
	@Test
	public void mapAsParameter() {
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockTransConfig(transport).sender().build();

		MapAsParam api = HttlApiBuilder.build(MapAsParam.class, sender);

		//When - null
		Map<Object, Object> map = null;
		HttlResponse response = api.map2response(map);
		//Then 
		HttlRequest request = response.getRequest();
		Assertions.assertThat(request.getParameters()).isEmpty();

		//When - empty
		map = new HashMap<Object, Object>();
		response = api.map2response(map);
		//Then
		request = response.getRequest();
		Assertions.assertThat(request.getParameters()).isEmpty();

		//When - number as key
		map.put(1, 2);
		response = api.map2response(map);
		//Then - ok
		request = response.getRequest();
		Assertions.assertThat(request.getParameters().getFirst("1")).isEqualTo("2");

		map.clear();

		//When - null key
		map.put(null, "value");
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getParameters()).isEmpty();

		//When - null value
		map.put("key", null);
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getParameters()).isEmpty();

		//When - empty list
		map.put("key", new ArrayList<String>());
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/x");

		ArrayList<Object> list = new ArrayList<Object>();
		list.add("first");
		list.add(333);
		map.put("key", list);
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/x?key=first&key=333");

		map.clear();

		//When - empty array
		map.put("key", new String[0]);
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/x");

		//When - array with 2 elements
		map.put("key", new Object[] { "first", 333 });
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/x?key=first&key=333");

		//When - primitive array with 2 elements
		map.put("key", new int[] { 111, 222 });
		response = api.map2response(map);
		//Then - skipped
		request = response.getRequest();
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/x?key=111&key=222");

	}

	static interface MapAsParam {

		@HttlCall("GET /x")
		HttlResponse map2response(@HttlVar Map<Object, Object> map);

	}

	@Test
	public void emptyParamaterApis() {
		HttlSender sender = HttlSender.with("www.example.com").build();
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

		@HttlCall("GET /missing")
		public void missing(@HttlVar String some);

	}

	static interface WrongApiEmptyName {

		@HttlCall("GET /empty")
		public void missing(@HttlVar("") String some);
	}

	@Test
	public void wrongGetWithBody() {
		HttlSender sender = HttlSender.with("www.example.com").build();
		try {
			HttlApiBuilder.with(sender).build(WrongApiGetWithBody.class);
			Assertions.fail("Expected " + HttlApiException.class.getSimpleName());
		} catch (HttlApiException abx) {
			Assertions.assertThat(abx.getMessage()).startsWith("Body not allowed");
		}
	}

	static interface WrongApiGetWithBody {

		@HttlCall("GET /whatever")
		public void whatever(@HttlBody String body); //GET with body is nonsense

	}
}
