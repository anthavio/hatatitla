package com.anthavio.httl;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.anthavio.cache.CacheBase;
import com.anthavio.cache.HeapMapCache;
import com.anthavio.httl.SenderRequest.EncodeStrategy;
import com.anthavio.httl.TestResponse.NameValue;
import com.anthavio.httl.cache.CachingExtractor;
import com.anthavio.httl.cache.CachingExtractorRequest;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.inout.ResponseBodyExtractors;
import com.anthavio.httl.inout.ResponseErrorHandler;
import com.anthavio.httl.inout.ResponseHandler;
import com.anthavio.httl.util.HttpHeaderUtil;

/**
 * 
 * @author martin.vanek
 *
 */
public class MarshallingExtractingTest {

	private JokerServer server = new JokerServer();

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();
	}

	public void devel() {
		HttpClient3Sender sender = new HttpClient3Config("localhost:" + 3333).buildSender();
		try {
			sender.GET("/").extract(String.class);
		} catch (SenderException sex) {
			sex.printStackTrace();
		}
	}

	@Test
	public void responseHandler() throws IOException {
		HttpClient4Sender sender = new HttpClient4Config("localhost:" + server.getHttpPort()).buildSender();
		PoolingClientConnectionManager cmanager = (PoolingClientConnectionManager) sender.getHttpClient()
				.getConnectionManager();

		TestResponseHandler handler = new TestResponseHandler();
		sender.GET("/").execute(handler);
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(200);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		sender.GET("/").param("dostatus", 500).execute(handler);
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		try {
			sender.GET("/").param("dostatus", 500).extract(String.class);
			Assert.fail("Preceding statement must throw SenderHttpStatusException");
		} catch (SenderHttpStatusException shsx) {
			//
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		TestResponseBodyExtractor badExtractor = new TestResponseBodyExtractor();
		ClassCastException extractException = new ClassCastException("I'm evil! I'm evil!");
		badExtractor.setExtractException(extractException);

		try {
			sender.GET("/").extract(badExtractor);
			Assert.fail("Preceding statement must throw " + extractException.getClass().getName());
		} catch (ClassCastException aiox) {
			assertThat(aiox).isEqualTo(extractException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		ArrayIndexOutOfBoundsException handleException = new ArrayIndexOutOfBoundsException("I'm baaad! I'm baaad!");
		//set global error handler
		sender.setErrorResponseHandler(handler);

		//now the same without response exception
		ExtractedBodyResponse<String> extract = sender.GET("/").param("dostatus", 500).extract(String.class);
		assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(extract.getResponse()).isEqualTo(handler.getResponse());
		assertThat(extract.getBody()).isNull(); //extracted body is null
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//same with ResponseBodyExtractor instead of resultType Class
		extract = sender.GET("/").param("dostatus", 500).extract(ResponseBodyExtractors.STRING);
		assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(extract.getResponse()).isEqualTo(handler.getResponse());
		assertThat(extract.getBody()).isNull(); //extracted body is null
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//now make that handler to throw exception from it's handle methods
		handler.setHandleException(handleException);

		try {
			extract = sender.GET("/").param("dostatus", 500).extract(String.class);
			Assert.fail("Preceding statement must throw " + handleException.getClass().getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		try {
			extract = sender.GET("/").param("dostatus", 500).extract(ResponseBodyExtractors.STRING);
			Assert.fail("Preceding statement must throw " + handleException.getClass().getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		try {
			sender.GET("/").param("dostatus", 500).extract(badExtractor);
			Assert.fail("Preceding statement must throw " + extractException.getClass().getName());
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		//unset global error response handler (but it still throws exception while handling responses)
		sender.setErrorResponseHandler(null);

		try {
			sender.GET("/").param("dostatus", 500).execute(handler);
		} catch (ArrayIndexOutOfBoundsException aiox) {
			assertThat(aiox).isEqualTo(handleException);
		}
		assertThat(handler.getResponse().getHttpStatusCode()).isEqualTo(500);
		assertThat(cmanager.getTotalStats().getLeased()).isEqualTo(0); //closed automaticaly

		sender.close();
	}

	@Test
	public void marshallingExtraction() throws IOException {
		String message = "Hello čobole";
		TestBodyRequest body = new TestBodyRequest(message);

		HttpClient4Config config = new HttpClient4Config("localhost:" + server.getHttpPort());
		config.setEncoding("ISO-8859-2");
		HttpSender sender = config.buildSender();

		//sender.setResponseExtractor(factory, "application/json");
		//sender.setRequestMarshaller("application/json", null);

		//different encoding for request and response
		PostRequest request = sender.POST("/").param("pmsg", message).param("dostatus", 201).body(body, "application/json")
				.accept("application/xml").header("Accept-Charset", "Cp1250").build();
		request.setUrlEncodingStrategy(EncodeStrategy.ENCODE);
		//charset is added from configuration
		assertThat(request.getFirstHeader("Content-Type").indexOf("charset=ISO-8859-2")).isNotEqualTo(-1);
		//System.out.println(request.getParameters().getFirst("pmsg"));
		ExtractedBodyResponse<TestResponse> extract = sender.extract(request, TestResponse.class);
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
		CachingExtractor cextractor = new CachingExtractor(sender, cache);
		CachingExtractorRequest<String> crequest = cextractor.request(request).softTTL(1, TimeUnit.MINUTES)
				.hardTTL(2, TimeUnit.MINUTES).build(String.class);

		String extract2 = cextractor.extract(crequest);
		System.out.println(extract2);
		/*
		CachingExtractorRequest<String> crequest = new CachingExtractorRequest<String>(request, String.class, 10l, 20l,
				TimeUnit.SECONDS, RefreshMode.ASYNC_REQUEST);
		cextractor.extract(crequest);
		*/
		//extract.getResponse()
		sender.close();
	}
}

class TestResponseBodyExtractor extends ResponseBodyExtractor<String> {

	private RuntimeException extractException; //simulate very bad extractor

	private SenderResponse response;

	@Override
	public String extract(SenderResponse response) throws IOException {
		this.response = response;
		if (extractException != null) {
			extractException.fillInStackTrace();
			throw extractException;
		}
		return HttpHeaderUtil.readAsString(response);
	}

	public RuntimeException getExtractException() {
		return extractException;
	}

	public void setExtractException(RuntimeException extractException) {
		this.extractException = extractException;
	}

	public SenderResponse getResponse() {
		return response;
	}
}

class TestResponseHandler implements ResponseHandler, ResponseErrorHandler {
	private SenderRequest request;
	private SenderResponse response;
	private Exception exception;
	private RuntimeException handleException; //simulate very bad handler

	@Override
	public void onResponse(SenderResponse response) throws IOException {
		this.response = response;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	@Override
	public void onRequestError(SenderRequest request, Exception exception) {
		this.request = request;
		this.exception = exception;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	@Override
	public void onResponseError(SenderResponse response, Exception exception) {
		this.response = response;
		this.exception = exception;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	@Override
	public void onErrorResponse(SenderResponse response) {
		this.response = response;
		if (handleException != null) {
			handleException.fillInStackTrace();
			throw handleException;
		}
	}

	public SenderRequest getRequest() {
		return request;
	}

	public void setRequest(SenderRequest request) {
		this.request = request;
	}

	public SenderResponse getResponse() {
		return response;
	}

	public void setResponse(SenderResponse response) {
		this.response = response;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}

	public void setHandleException(RuntimeException handleException) {
		this.handleException = handleException;
	}

}
