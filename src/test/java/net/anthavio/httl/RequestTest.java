package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.anthavio.httl.Authentication.Scheme;
import net.anthavio.httl.HttlBody.Type;
import net.anthavio.httl.HttlParameterSetter.ConfigurableParamSetter;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlRequestBuilders.SenderBodyRequestBuilder;
import net.anthavio.httl.HttlRequestBuilders.SenderNobodyRequestBuilder;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpUrlConfig;
import net.anthavio.httl.util.MockSenderConfig;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class RequestTest {

	@Test
	public void urlPath() {
		HttlRequest request;

		request = HttlSender.For("www.example.com").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/file");

		request = HttlSender.For("www.example.com/").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/file");

		request = HttlSender.For("www.example.com/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/path/file");

		//When too many /
		request = HttlSender.For("www.example.com/path/").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/path/file");

		//When too little /
		request = HttlSender.For("www.example.com/path").build().GET("file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/path/file");

		//When port
		request = HttlSender.For("www.example.com:8080/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com:8080/path/file");

		//When http
		request = HttlSender.For("http://www.example.com/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/path/file");

		//When https
		request = HttlSender.For("https://www.example.com/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("https://www.example.com/path/file");

		//When http + port
		request = HttlSender.For("http://www.example.com:8080/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com:8080/path/file");

		//When https + port
		request = HttlSender.For("https://www.example.com:9696/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("https://www.example.com:9696/path/file");

		//When username:password in a URL - it is omitted
		request = HttlSender.For("https://username:password@www.example.com:9696/path").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("https://www.example.com:9696/path/file");

		//When query in URL - it is omitted
		request = HttlSender.For("www.example.com/path;matrix=value?query=value").build().GET("/file").build();
		assertThat(request.getUrl().toString()).isEqualTo("http://www.example.com/path;matrix=value/file");

	}

	@Test
	public void parameters() {
		HttlSender sender = new HttpUrlConfig("www.example.com").build();
		assertThat(sender.getConfig().getUrl().toString()).isEqualTo("http://www.example.com"); //add http prefix and remove path suffix
		//SimpleHttpSender sender = null;

		SenderNobodyRequestBuilder builder = sender.GET("/get");
		//
		HttlRequest req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get");

		builder.param("p0", (Object) null);
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get");

		builder.param("p1", "");
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get?p1=");

		builder.param("p2", 2);
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get?p1=&p2=2");

		builder.matrix("m0", null);//null is skipped by default
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get?p1=&p2=2");

		builder.matrix("m1", "");//blank is not skipped by default
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get;m1=?p1=&p2=2");

		builder.param(";m2", 2);
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/get;m1=;m2=2?p1=&p2=2");
	}

	@Test
	public void requestBody() throws IOException {
		//Given sender
		HttlSender sender = new HttpUrlConfig("www.example.com").build();

		//When - only body
		SenderBodyRequestBuilder builder = sender.POST("/x").body("<x></x>", "application/xml");
		HttlRequest request = builder.build();
		//Then
		assertThat(request.getBody().getPayload()).isEqualTo("<x></x>");
		assertThat(request.getMediaType()).isEqualTo("application/xml");
		assertThat(request.getCharset()).isEqualTo("utf-8");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/xml; charset=utf-8");

		//When - only parameters 
		builder = sender.POST("/x").param("p", "v");
		request = builder.build();
		//Then - parameters = POST body
		assertThat(request.getPathAndQuery()).isEqualTo("/x");
		assertThat(request.getBody().getPayload()).isEqualTo("p=v");

		assertThat(request.getMediaType()).isEqualTo("application/x-www-form-urlencoded");
		assertThat(request.getCharset()).isEqualTo("utf-8");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded; charset=utf-8");

		//When - parameters and body
		builder.body("[]", "application/json; charset=Cp1252");
		request = builder.build();
		//Then
		assertThat(request.getPathAndQuery()).isEqualTo("/x?p=v");
		assertThat(request.getBody().getPayload()).isEqualTo("[]");
		assertThat(request.getMediaType()).isEqualTo("application/json");
		assertThat(request.getCharset()).isEqualTo("Cp1252");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=Cp1252");

		//When - Request media type (defalut) is NOT configured

		//Then - RequestException
		try {
			request = sender.POST("/x").body("brrrrrrrrrrr").build();
			Assertions.fail("Expected " + HttlRequestException.class.getName());
		} catch (HttlRequestException rx) {
			//this is expected
		}

		//When - Content-Type passed as separate header
		request = sender.POST("/x").header("Content-Type", "text/plain").body("brrrrrrrrrrr").build();
		//Then
		assertThat(request.getBody().getPayload()).isEqualTo("brrrrrrrrrrr");
		assertThat(request.getMediaType()).isEqualTo("text/plain");
		assertThat(request.getCharset()).isEqualTo("utf-8");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("text/plain; charset=utf-8");

		//When - configure Request media type (defalut)
		HttpUrlConfig config = new HttpUrlConfig("www.example.com");
		config.setRequestMediaType("text/plain");
		config.setEncoding("ISO-8859-2");
		sender = config.build();

		//Then - ok now
		request = sender.POST("/x").body("brrrrrrrrrrr").build();
		assertThat(request.getBody().getPayload()).isEqualTo("brrrrrrrrrrr");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("text/plain; charset=ISO-8859-2");
		assertThat(request.getMediaType()).isEqualTo("text/plain");
		assertThat(request.getCharset()).isEqualTo("ISO-8859-2");
	}

	@Test
	public void postBodyCaching() {
		HttlSender sender = new MockSenderConfig().build();

		ByteArrayInputStream stream = new ByteArrayInputStream("brrrrrrrrrrrrr".getBytes());
		//When - default is non caching
		HttlRequest request = sender.POST("/whatever").body(stream, "text/plain").build();
		//Then - original stream
		Assertions.assertThat(request.getBody().getType()).isEqualTo(Type.STREAM);
		Assertions.assertThat(request.getBody().getPayload()).isEqualTo(stream);

		//When - caching parameter true
		request = sender.POST("/whatever").body(stream, "text/plain", true).build();
		//Then - cached to byte[]
		Assertions.assertThat(request.getBody().getType()).isEqualTo(Type.BYTES);
		Assertions.assertThat(request.getBody().getPayload()).isExactlyInstanceOf(byte[].class);

		//Given Mashalled payload
		TestBodyRequest bean = new TestBodyRequest("Something");
		//When - default
		request = sender.POST("/whatever").body(bean, "application/json").build();
		//Then - original bean is there
		Assertions.assertThat(request.getBody().getType()).isEqualTo(Type.MARSHALL);
		Assertions.assertThat(request.getBody().getPayload()).isEqualTo(bean);

		//When - cached parameter true
		request = sender.POST("/whatever").body(bean, "application/json", true).build();
		//Then - bean is marshalled into bytes
		Assertions.assertThat(request.getBody().getType()).isEqualTo(Type.BYTES);
		Assertions.assertThat(request.getBody().getPayload()).isExactlyInstanceOf(byte[].class);

		//When - caching and wrong mime type to marshall
		try {
			sender.POST("/whatever").body(bean, "unsupported/type", true).build();
			//Then - exception intantly
			Assertions.fail("Expected " + HttlRequestException.class.getName());
		} catch (HttlRequestException rx) {
			Assertions.assertThat(rx.getMessage()).startsWith("Marshaller not found");
		}
	}

	@Test
	public void mapAsParameter() {
		//Given - default settings
		HttlSender sender = new HttpUrlConfig("www.example.com").build();
		HttlRequest request;
		//When
		Map<String, Object> map = new HashMap<String, Object>();
		request = sender.GET("/path").param("map", map).build();
		//Then
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/path");

	}

	@Test
	public void dateAsParameter() throws UnsupportedEncodingException {

		//Given - default settings
		HttlSender sender = new HttpUrlConfig("www.example.com").build();

		//When - Date as parameter
		SenderNobodyRequestBuilder builder = sender.OPTIONS("/options");
		Date date = new Date();
		builder.param("d", date, 32, "ZXZX");
		HttlRequest request = builder.build();
		//Then - default pattern is used
		String sdate = new SimpleDateFormat(ConfigurableParamSetter.DEFAULT_DATE_PATTERN).format(date);
		sdate = URLEncoder.encode(sdate, "UTF-8");
		assertThat(request.getPathAndQuery()).isEqualTo("/options?d=" + sdate + "&d=32&d=ZXZX");

		//Given - custom date format
		String pattern = "yyyy-MM-dd";
		String expected = new SimpleDateFormat(pattern).format(date);

		//When - pattern set as parameter
		request = sender.OPTIONS("/options").param("d", date, pattern).build();
		//Then
		assertThat(request.getPathAndQuery()).isEqualTo("/options?d=" + expected);

		//When - pattern set into global ParamSetter
		sender.getConfig().setParamSetter(new ConfigurableParamSetter(pattern));
		request = sender.DELETE("/delete").param("d", date).build();
		//Then
		assertThat(request.getPathAndQuery()).isEqualTo("/delete?d=" + expected);
	}

	@Test
	public void arraysAndCollections() {
		HttlSender sender = new HttpUrlConfig("www.example.com").build();

		SenderNobodyRequestBuilder builder = sender.DELETE("/delete");
		builder.param(";m3", 31, 32, 33);
		HttlRequest req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/delete;m3=31;m3=32;m3=33");

		builder = sender.DELETE("/delete");
		builder.param(";m3", "31", null, ""); //null and empty (vararg)
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/delete;m3=31;m3=");

		builder = sender.DELETE("/delete");
		builder.param(";m3", Arrays.asList("31", null, "")); //null and empty (collection)
		req = builder.build();
		assertThat(req.getMethod()).isEqualTo(Method.DELETE);
		assertThat(req.getPathAndQuery()).isEqualTo("/delete;m3=31;m3=");

		builder = sender.DELETE("/delete");
		builder.param(";m3", new String[] { "31", null, "" }); //null and empty (array)
		req = builder.build();
		assertThat(req.getPathAndQuery()).isEqualTo("/delete;m3=31;m3=");
	}

	@Test
	public void headersAndParams() {
		HttlSender sender = new MockSenderConfig().build();

		HttlRequest request = sender.GET("/").build();
		assertThat(request.getMethod()).isEqualTo(Method.GET);
		assertThat(request.getPathAndQuery()).isEqualTo("/");
		assertThat(request.getParameters()).isEmpty();
		assertThat(request.getHeaders()).isEmpty();

		request = sender.POST("/").build();
		assertThat(request.getMethod()).isEqualTo(Method.POST);
		assertThat(request.getPathAndQuery()).isEqualTo("/");
		assertThat(request.getParameters()).isEmpty();
		assertThat(request.getHeaders()).isEmpty();
		//assertThat(request.getFirstHeader(Constants.Accept_Charset)).isEqualTo("utf-8");

		String path = "/path/to/somewhere";
		SenderNobodyRequestBuilder builder = sender.HEAD(path);//.params(parameters).setHeaders(headers);

		builder.param("p1", Arrays.asList("y"));
		builder.param("p2", Arrays.asList("a", "b", "c"));
		builder.param("p3", Collections.EMPTY_LIST);
		builder.param("p4", (String) null);
		builder.param("p5", Arrays.asList(""));

		builder.header("h1", Arrays.asList("v1"));
		builder.header("h2", Arrays.asList("v21", "v22", "v23"));
		builder.header("h3", Collections.EMPTY_LIST);
		builder.header("h4", (String) null);
		builder.header("h5", Arrays.asList(""));

		request = builder.build();
		assertThat(request.getMethod()).isEqualTo(Method.HEAD);

		//assertThat(request.getParameters()).isEqualTo(parameters);
		assertThat(request.getParameters().size()).isEqualTo(4);
		assertThat(request.getParameters().names().size()).isEqualTo(4);

		assertThat(request.getParameters().get("p1")).hasSize(1);
		assertThat(request.getParameters().getFirst("p1")).isEqualTo("y");
		assertThat(request.getParameters().getLast("p1")).isEqualTo("y");
		assertThat(request.getParameters().get("p2")).hasSize(3);
		assertThat(request.getParameters().getFirst("p2")).isEqualTo("a");
		assertThat(request.getParameters().getLast("p2")).isEqualTo("c");
		assertThat(request.getParameters().get("p3")).hasSize(0);
		assertThat(request.getParameters().getFirst("p3")).isNull();
		assertThat(request.getParameters().getLast("p3")).isNull();
		assertThat(request.getParameters().get("p4")).isNull();

		assertThat(request.getParameters().get("p5")).hasSize(1);
		assertThat(request.getParameters().getFirst("p5")).isEqualTo("");
		assertThat(request.getParameters().getLast("p5")).isEqualTo("");

		//assertThat(request.getHeaders()).isEqualTo(headers);
		assertThat(request.getHeaders()).hasSize(5);
		assertThat(request.getHeaders().names()).hasSize(5);

		assertThat(request.getHeaders().get("h1")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h1")).isEqualTo("v1");
		assertThat(request.getHeaders().getLast("h1")).isEqualTo("v1");

		assertThat(request.getHeaders().get("h2")).hasSize(3);
		assertThat(request.getHeaders().getFirst("h2")).isEqualTo("v21");
		assertThat(request.getHeaders().getLast("h2")).isEqualTo("v23");

		assertThat(request.getHeaders().get("h3")).hasSize(0);
		assertThat(request.getHeaders().getFirst("h3")).isNull();
		assertThat(request.getHeaders().getLast("h3")).isNull();

		assertThat(request.getHeaders().get("h4")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h4")).isNull();
		assertThat(request.getHeaders().getLast("h4")).isNull();

		assertThat(request.getHeaders().get("h5")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h5")).isEqualTo("");
		assertThat(request.getHeaders().getLast("h5")).isEqualTo("");

		//parameteres allow duplicit names
		builder.param("param", "something");
		assertThat(builder.build().getParameters().get("param")).hasSize(1);
		builder.param("param", "something");
		assertThat(builder.build().getParameters().get("param")).hasSize(2);

		//headers allow duplicit names
		builder.header("header", "something");
		assertThat(builder.build().getHeaders().get("header")).hasSize(1);
		builder.header("header", "something");
		assertThat(builder.build().getHeaders().get("header")).hasSize(2);
	}

	@Test
	public void testConfigDefaults() {
		//Given
		String url = "http://www.example.com:8080";
		HttlSender sender = HttlSender.For(url).build();
		SenderBuilder config = sender.getConfig();
		//Then
		assertThat(sender.getConfig().getEncoding()).isEqualTo("utf-8");
		assertThat(sender.getConfig().getAuthentication()).isNull();

		//When - default authentication is BASIC and preepmtive
		Authentication auth = new Authentication(Authentication.Scheme.BASIC, "user", "pass");
		//Then
		assertThat(auth.getScheme()).isEqualTo(Authentication.Scheme.BASIC);
		assertThat(auth.getPreemptive()).isEqualTo(true);

		//When
		HttlRequest request = sender.POST("/x").body("b", "text/plain").build();
		//Then
		assertThat(request.getPathAndQuery()).isEqualTo("/x");
		assertThat(request.getBody().getPayload()).isEqualTo("b");
		assertThat(request.getMediaType()).isEqualTo("text/plain");
		assertThat(request.getCharset()).isEqualTo("utf-8");

		//When
		config.setEncoding("utf-16"); //Java capitalizes it into UTF-16
		config.setRequestMediaType("application/xml");
		config.setResponseMediaType("application/json");

		request = config.build().POST("/x").body("b").build();
		//Then
		assertThat(request.getMediaType()).isEqualTo("application/xml");
		assertThat(request.getCharset()).isEqualTo("UTF-16");

		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/xml; charset=UTF-16");
		assertThat(request.getFirstHeader(HttlConstants.Accept)).isEqualTo("application/json");

		sender.close();
	}

	@Test
	public void http4() {
		String url = "http://www.hostname.com:8080/somewhere";
		HttpClient4Config config = new HttpClient4Config(url);
		Authentication authentication = new Authentication(Scheme.DIGEST, "user", "pass", false);
		config.setAuthentication(authentication);
		HttlSender sender = config.build();

		assertThat(sender.getConfig().getUrl().toString()).isEqualTo("http://www.hostname.com:8080/somewhere"); // file(path) URL part is thrown away
		assertThat(sender.getConfig().getEncoding()).isEqualTo("utf-8");
		assertThat(sender.getConfig().getAuthentication().getPreemptive()).isFalse();

		sender.close();
	}

}
