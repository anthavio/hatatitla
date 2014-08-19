package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.IllegalFormatFlagsException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.impl.HeapMapCache;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.TestResponse.NameValue;
import net.anthavio.httl.marshall.HttlBytesExtractor;
import net.anthavio.httl.marshall.HttlStringExtractor;
import net.anthavio.httl.transport.HttpClient4Transport;
import net.anthavio.httl.util.GenericType;
import net.anthavio.httl.util.HttpHeaderUtil;
import net.anthavio.httl.util.JsonBuilder;
import net.anthavio.httl.util.MockTransConfig;
import net.anthavio.httl.util.MockTransport;

import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author martin.vanek
 *
 */
public class MarshallingExtractingTest {

	private static JokerServer server = new JokerServer();

	@BeforeClass
	public static void setup() throws Exception {
		server.start();
	}

	@AfterClass
	public static void destroy() throws Exception {
		server.stop();
	}

	public void devel() {
		HttlSender sender = HttlSender.with("localhost:" + 3333).httpClient3().sender().build();
		try {
			sender.GET("/").extract(String.class);
		} catch (HttlException sex) {
			sex.printStackTrace();
		}
	}

	@Test
	public void customResponseUnmarshaller() {

		//Given - Service returns http 555 and JSON with error message
		MockTransport transport = new MockTransport();
		MockTransConfig config = new MockTransConfig(transport);
		HttlSender sender = config.sender().build();
		transport.setStaticResponse(555, "application/json", JsonBuilder.OBJECT().field("message", "Shit happend!").end()
				.getJson());

		//When - default settings
		try {
			sender.GET("/evil").extract(String.class);
			Assertions.fail("Expected " + HttlStatusException.class.getName());
			//Then - HttlStatusException because of 555 response
		} catch (HttlStatusException rsx) {
			Assertions.assertThat(rsx.getResponse().getHttpStatusCode()).isEqualTo(555);
		}

		// And 

		HttlBodyUnmarshaller evilUnmar = new HttlBodyUnmarshaller.ConfigurableUnmarshaller("application/json", 555) {

			private ObjectMapper mapper = new ObjectMapper();

			@Override
			public Object doUnmarshall(HttlResponse response, Type resultType) throws IOException {
				Map value = mapper.readValue(response.getReader(), Map.class);
				throw new ArrayIndexOutOfBoundsException((String) value.get("message")); // some API exception in real world
			}
		};

		// When - Custom ResponseUnmarshaller
		sender = config.sender().setUnmarshaller(evilUnmar).build();
		try {

			sender.GET("/evil").extract(TestBodyRequest.class);
			//Then - Custom ArrayIndexOutOfBoundsException is thrown
		} catch (ArrayIndexOutOfBoundsException isx) {
			Assertions.assertThat(isx.getMessage()).isEqualTo("Shit happend!");
		}

		sender.close();
	}

	/**
	 *
	 */
	@Test
	public void builtinResponseUnmarshallers() throws IOException {

		HttlSender sender = HttlBuilder.httpClient4("localhost:" + server.getHttpPort()).sender().build();
		HttpClient4Transport transport = (HttpClient4Transport) sender.getTransport();
		PoolingClientConnectionManager cmanager = (PoolingClientConnectionManager) transport.getHttpClient()
				.getConnectionManager();

		// When 
		ExtractedResponse<String> extracted1s = sender.GET("/").extract(String.class);
		// Then
		Assertions.assertThat(extracted1s.getResponse().getHttpStatusCode()).isEqualTo(200);
		Assertions.assertThat(extracted1s.getBody()).startsWith("<h1> Hello");
		Assertions.assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		// When 
		ExtractedResponse<byte[]> extracted1b = sender.GET("/").extract(byte[].class);
		// Then 
		Assertions.assertThat(extracted1b.getResponse().getHttpStatusCode()).isEqualTo(200);
		Assertions.assertThat(new String(extracted1b.getBody(), "utf-8")).startsWith("<h1> Hello");
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		// When
		try {
			sender.GET("/").extract(new GenericType<List<String>>() {
			});
			Assertions.fail("Preceding statement must throw " + HttlProcessingException.class.getName());
		} catch (HttlProcessingException isx) {
			Assertions.assertThat(isx.getMessage()).startsWith("No Unmarshaller for Response");
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		// When - http 500 String
		try {
			sender.GET("/").param("dostatus", 500).extract(String.class);
			Assertions.fail("Previous statement should throw " + HttlStatusException.class.getName());
		} catch (HttlStatusException rsx) {
			//Then - HttlStatusException
			Assertions.assertThat(rsx.getResponse().getHttpStatusCode()).isEqualTo(500);
			Assertions.assertThat(rsx.getResponseBody()).contains("Dostatus 500");
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//Given - Allow HttlStringExtractor to accept 500
		sender.getConfig().setStringExtractor(new HttlStringExtractor(200, 500));

		//When - http 500
		ExtractedResponse<String> extracted2s = sender.GET("/").param("dostatus", 500).extract(String.class);
		Assertions.assertThat(extracted2s.getResponse().getHttpStatusCode()).isEqualTo(500);
		Assertions.assertThat(extracted2s.getBody()).contains("Dostatus 500");
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		// When - http 500 byte[]
		try {
			sender.GET("/").param("dostatus", 500).extract(byte[].class);
			Assertions.fail("Previous statement should throw " + HttlStatusException.class.getName());
		} catch (HttlStatusException rsx) {
			//Then - HttlStatusException
			Assertions.assertThat(rsx.getResponse().getHttpStatusCode()).isEqualTo(500);
			Assertions.assertThat(rsx.getResponseBody()).contains("Dostatus 500");
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//Given - Allow HttlBytesExtractor to accept 500
		sender.getConfig().setBytesExtractor(new HttlBytesExtractor(200, 500));

		ExtractedResponse<byte[]> extracted2b = sender.GET("/").param("dostatus", 500).extract(byte[].class);
		// Then - No exception
		Assertions.assertThat(extracted2b.getResponse().getHttpStatusCode()).isEqualTo(500);
		Assertions.assertThat(new String(extracted2b.getBody(), "utf-8")).contains("Dostatus 500");
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		sender.close();
	}

	@Test
	public void responseHandler() throws IOException {
		//Given - HttpClient4Sender because we can precisely track connection lasing and returning
		HttlSender sender = HttlSender.with("localhost:" + server.getHttpPort()).httpClient4().sender().build();
		HttpClient4Transport transport = (HttpClient4Transport) sender.getTransport();
		PoolingClientConnectionManager cmanager = (PoolingClientConnectionManager) transport.getHttpClient()
				.getConnectionManager();

		TestResponseHandler handler = new TestResponseHandler();
		sender.GET("/").execute(handler);
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(200);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		sender.GET("/").param("dostatus", 500).execute(handler);
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		ArrayIndexOutOfBoundsException handleException = new ArrayIndexOutOfBoundsException("I'm baaad! I'm baaad!");
		//When - Break handler to throw exception from it's handle methods
		handler.setHandleException(handleException);

		try {
			sender.GET("/").param("dostatus", 500).execute(handler);
			Assert.fail("Expected " + ArrayIndexOutOfBoundsException.class.getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//Failing extractor test

		TestResponseBodyExtractor failingExtractor = new TestResponseBodyExtractor();
		IllegalFormatFlagsException extractException = new IllegalFormatFlagsException("I'm evil! I'm evil!");
		failingExtractor.simulatedException = extractException;

		try {
			sender.GET("/").extract(failingExtractor);
			Assert.fail("Expected " + HttlProcessingException.class.getName());
		} catch (HttlProcessingException hpx) {
			assertThat(hpx.getCause()).isEqualTo(extractException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		try {
			sender.GET("/").param("dostatus", 501).extract(failingExtractor);
			Assert.fail("Expected " + HttlProcessingException.class.getName());
		} catch (HttlProcessingException hpx) {
			assertThat(hpx.getCause()).isEqualTo(extractException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		ExtractedResponse<String> extract;
		/*
		//Then
		try {
			extract = sender.GET("/").param("dostatus", 501).extract(String.class);
			Assert.fail("Preceding statement must throw " + handleException.getClass().getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(501);
		assertThat(handler.getException()).isNull();
		//System.out.println("zzzzzzzzz " + handler.getResponse());
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		try {
			extract = sender.GET("/").param("dostatus", 502).extract(HttlStringExtractor.DEFAULT);
			Assert.fail("Preceding statement must throw " + handleException.getClass().getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(501);
		assertThat(handler.getException()).isNull();

		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly
		*/

		try {
			sender.GET("/").param("dostatus", 500).extract(String.class);
			Assert.fail("Expected " + HttlStatusException.class.getName());
		} catch (HttlStatusException shsx) {
			//expected
			Assertions.assertThat(shsx.getResponse().getHttpStatusCode()).isEqualTo(500);
			Assertions.assertThat(shsx.getResponse().getMediaType()).isEqualTo("text/html");
			Assertions.assertThat(shsx.getResponseBody()).contains("Dostatus 500");
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//Given - allow HttlStringExtractor http 500
		sender.getConfig().setStringExtractor(new HttlStringExtractor(0, 1000));
		//now the same without response exception
		extract = sender.GET("/").param("dostatus", 500).extract(String.class);
		assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(500);
		//assertThat(extract.getResponse()).isEqualTo(handler.getResponse());
		//assertThat(extract.getBody()).isNull(); //extracted body is null
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		sender.close();
	}

	@Test
	public void marshallingExtraction() throws IOException {
		String message = "Hello čobole";
		TestBodyRequest body = new TestBodyRequest(message);

		HttlSender sender = HttlSender.with("localhost:" + server.getHttpPort()).httpClient4().setCharset("ISO-8859-2")
				.sender().build();

		//sender.setResponseExtractor(factory, "application/json");
		//sender.setRequestMarshaller("application/json", null);

		//different encoding for request and response
		HttlRequest request = sender.POST("/").param("pmsg", message).param("dostatus", 201).body(body, "application/json")
				.header("Accept", "application/xml").header("Accept-Charset", "Cp1250").build();
		//request.setEncodeParams(true);
		//charset is added from configuration
		assertThat(request.getMediaType()).isEqualTo("application/json");
		assertThat(request.getCharset()).isEqualTo("ISO-8859-2");
		assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=ISO-8859-2");
		//System.out.println(request.getParameters().getFirst("pmsg"));
		ExtractedResponse<TestResponse> extract = sender.extract(request, TestResponse.class);
		assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(201);
		assertThat(extract.getBody().getRequest().getMessage()).isEqualTo(message); //č character must be preserved!
		List<NameValue> parameters = extract.getBody().getRequest().getParameters();

		for (NameValue nameValue : parameters) {
			if (nameValue.getName().equals("pmsg")) {
				assertThat(nameValue.getValue()).isEqualTo(message); //č character must be preserved!
			}
			//System.out.println(nameValue.getName() + " " + nameValue.getValue());
		}

		CacheBase<Object> cache = new HeapMapCache<Object>();
		//CachingExtractor cextractor = new CachingExtractor(sender, cache);
		//CachingExtractorRequest<String> crequest = cextractor.from(request).hardTtl(2, TimeUnit.MINUTES).softTtl(1, TimeUnit.MINUTES)
		//		.build(String.class);

		//CacheEntry<String> extract2 = cextractor.extract(crequest);
		//System.out.println(extract2);
		/*
		CachingExtractorRequest<String> crequest = new CachingExtractorRequest<String>(request, String.class, 10l, 20l,
				TimeUnit.SECONDS, RefreshMode.ASYNC_REQUEST);
		cextractor.extract(crequest);
		*/
		//extract.getResponse()
		sender.close();
	}
}

class TestResponseBodyExtractor implements HttlResponseExtractor<String> {

	public RuntimeException simulatedException; //simulate very bad extractor

	public HttlResponse response;

	@Override
	public String extract(HttlResponse response) throws IOException {
		this.response = response;
		if (simulatedException != null) {
			simulatedException.fillInStackTrace();
			throw simulatedException;
		}
		return HttpHeaderUtil.readAsString(response);
	}

}

/*
class TestRequestInterceptor implements HttlRequestInterceptor {

	public HttlRequest request;
	public RuntimeException simulatedException; //simulate failing interceptor

	@Override
	public void onSend(HttlRequest request) {
		this.request = request;
		if (simulatedException != null) {
			simulatedException.fillInStackTrace();
			throw simulatedException;
		}
	}

}

class TestResponseInterceptor implements HttlResponseInterceptor {

	public HttlResponse response;
	public RuntimeException simulatedException; //simulate failing interceptor

	@Override
	public void onRecieve(HttlResponse response) {
		this.response = response;
		if (simulatedException != null) {
			simulatedException.fillInStackTrace();
			throw simulatedException;
		}
	}

}
*/
class TestResponseHandler implements HttlResponseHandler {
	private HttlRequest request;
	private HttlResponse response;
	private Exception exception;
	private RuntimeException handleException; //simulate failing handler
	private AtomicInteger invocationCount = new AtomicInteger(0);

	@Override
	public void onResponse(HttlResponse response) throws IOException {
		this.invocationCount.incrementAndGet();
		this.response = response;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	@Override
	public void onFailure(HttlRequest request, Exception exception) {
		this.invocationCount.incrementAndGet();
		this.request = request;
		this.exception = exception;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	public HttlRequest getRequest() {
		return request;
	}

	public HttlResponse getResponse() {
		return response;
	}

	public Exception getException() {
		return exception;
	}

	public void setHandleException(RuntimeException handleException) {
		this.handleException = handleException;
	}

}
