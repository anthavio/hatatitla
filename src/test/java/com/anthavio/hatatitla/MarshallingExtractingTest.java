package com.anthavio.hatatitla;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.anthavio.hatatitla.cache.CachingExtractor;
import com.anthavio.hatatitla.cache.CachingExtractorRequest;
import com.anthavio.hatatitla.cache.RequestCache;
import com.anthavio.hatatitla.cache.SimpleRequestCache;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

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

	public void test() throws IOException {
		TestBodyRequest body = new TestBodyRequest("Hello píčo");

		HttpSender sender = new HttpClient4Config("localhost:" + server.getHttpPort()).buildSender();

		//sender.setResponseExtractor(factory, "application/json");
		//sender.setRequestMarshaller("application/json", null);

		PostRequest request = sender.POST("/").param("xxx", "Hello píčo").param("dostatus", 200)
				.body(body, "application/json; charset=iso-8859-2").accept("application/xml")
				.header("Accept-Charset", "Cp1250").build();

		ExtractedBodyResponse<TestResponse> extract = request.extract(TestResponse.class);
		System.out.println(extract.getBody().getRequest().getMessage());

		RequestCache<Serializable> cache = new SimpleRequestCache<Serializable>();
		CachingExtractor cextractor = new CachingExtractor(sender, cache);
		CachingExtractorRequest<String> crequest = cextractor.with(request).softTtl(1, TimeUnit.MINUTES)
				.hardTtl(2, TimeUnit.MINUTES).build(String.class);

		String extract2 = cextractor.extract(crequest);
		System.out.println(extract2);
		/*
		CachingExtractorRequest<String> crequest = new CachingExtractorRequest<String>(request, String.class, 10l, 20l,
				TimeUnit.SECONDS, RefreshMode.ASYNC_REQUEST);
		cextractor.extract(crequest);
		*/
		//extract.getResponse()

	}
}
