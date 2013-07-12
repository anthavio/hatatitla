package com.anthavio.httl.example;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.Marshaller;

import org.testng.Assert;

import com.anthavio.cache.CacheBase;
import com.anthavio.cache.HeapMapCache;
import com.anthavio.httl.Authentication;
import com.anthavio.httl.GetRequest;
import com.anthavio.httl.HttpClient3Config;
import com.anthavio.httl.HttpClient3Sender;
import com.anthavio.httl.HttpClient4Config;
import com.anthavio.httl.HttpClient4Sender;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.HttpURLConfig;
import com.anthavio.httl.HttpURLSender;
import com.anthavio.httl.SenderRequest.ValueStrategy;
import com.anthavio.httl.async.ExecutorServiceBuilder;
import com.anthavio.httl.cache.CachedResponse;
import com.anthavio.httl.cache.CachingExtractor;
import com.anthavio.httl.cache.CachingExtractorRequest;
import com.anthavio.httl.cache.CachingRequest;
import com.anthavio.httl.cache.CachingRequest.RefreshMode;
import com.anthavio.httl.cache.CachingSender;
import com.anthavio.httl.inout.Jackson2ExtractorFactory;
import com.anthavio.httl.inout.Jackson2RequestMarshaller;
import com.anthavio.httl.inout.JaxbRequestMarshaller;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * We will be using Github API http://developer.github.com/v3/
 * 
 * @author martin.vanek
 *
 */
public class ExamplesTest {

	public static void main(String[] args) {
		cachingScheduled();
	}

	public static void fluent() {
		//Create sender with utf-8 encoding, default timeouts and connection pool
		HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");

		ExtractedBodyResponse<String> extracted1 = sender.GET("/users").param("since", 333).extract(String.class);
		//Just print unprocessed JSON String
		System.out.println(extracted1.getBody());

		//Free connection pool
		sender.close();
	}

	public static void tradition() {
		//Sender can be built from Configuration
		HttpClient4Config config = new HttpClient4Config("https://api.github.com");
		//Configuration example follows, but here comes sneak peek
		config.setReadTimeoutMillis(5 * 1000);
		HttpClient4Sender sender = new HttpClient4Sender(config);

		GetRequest request = new GetRequest("/users");
		request.setParameter("since", 333);
		ExtractedBodyResponse<String> extracted = sender.extract(request, String.class);
		//Just print unprocessed JSON String
		System.out.println(extracted.getBody());

		//Free connection pool
		sender.close();
	}

	public static void configuration() {
		HttpClient4Config config = new HttpClient4Config("http://httpbin.org");

		//That pesky IIS wants Cyrillic? No problem!
		config.setEncoding("Cp1251"); //default is utf-8

		//Life if boring without timeouts
		config.setConnectTimeoutMillis(3 * 1000); //default is 5 seconds
		config.setReadTimeoutMillis(10 * 1000); //default is 20 seconds

		//Connection pooling for maximal throughput
		config.setPoolMaximumSize(60); //default is 10
		//Timeout for getting connection from pool
		config.setPoolAcquireTimeoutMillis(5 * 1000); //default is 3 seconds
		//TTL for connections in pool
		config.setPoolReleaseTimeoutMillis(5 * 60 * 1000); //default is 65 seconds

		//BASIC and DIGEST Autentication at your service! BASIC is preemptive by default.
		config.setAuthentication(Authentication.BASIC("myusername", "mypassword"));

		config.setFollowRedirects(true); //default is false
		config.setGzipRequest(true); //default is false

		//What to do with null or "" parameters?
		config.setNullValueStrategy(ValueStrategy.SKIP); //default is KEEP
		config.setEmptyValueStrategy(ValueStrategy.SKIP); //default is KEEP

		//Tired of setting Accept Header to every request?
		config.setDefaultAccept("application/json"); //default is none

		HttpClient4Sender sender = config.buildSender();
		//...send send send...
		sender.close();
	}

	public static void senders() {
		//Easy to start with
		//No additional dependency - vanilla java 
		HttpURLConfig urlConfig = new HttpURLConfig("https://graph.facebook.com");
		HttpURLSender urlSender = urlConfig.buildSender();

		//Recommended choice
		//Dependency - http://hc.apache.org/httpcomponents-client-ga/
		//java.lang.NoClassDefFoundError: org/apache/http/client/methods/HttpRequestBase
		HttpClient4Config http4config = new HttpClient4Config("https://api.twitter.com");
		HttpClient4Sender http4sender = http4config.buildSender();

		//Legacy choice
		//Dependency - http://hc.apache.org/httpclient-3.x/
		//java.lang.NoClassDefFoundError: org/apache/commons/httpclient/HttpMethodBase
		HttpClient3Config http3config = new HttpClient3Config("https://api.twitter.com");
		HttpClient3Sender http3sender = http3config.buildSender();
	}

	public static void json() {
		//Precondition is to have Jackson 1 or Jackson 2 on classpath, otherwise following exception will occur
		//java.lang.IllegalArgumentException: Request body marshaller not found for application/json
		//java.lang.IllegalArgumentException: No extractor factory found for mime type application/json
		HttpClient4Sender sender = new HttpClient4Sender("http://httpbin.org");

		//Send HttpbinIn instance marshalled as JSON document
		HttpbinIn binIn = new HttpbinIn();
		binIn.setSomeDate(new Date());
		binIn.setSomeString("Hello!");

		//Using extract method will parse returned Httpbin JSON document into HttpbinOut instance
		ExtractedBodyResponse<HttpbinOut> extract = sender.POST("/post").body(binIn, "application/json")
				.extract(HttpbinOut.class);

		HttpbinOut body = extract.getBody(); //voila!

		sender.close();

		//Tweak existing JSON RequestMarshaller - assume that Jackson 2 is present 
		Jackson2RequestMarshaller jsonMarshaller = (Jackson2RequestMarshaller) sender
				.getRequestMarshaller("application/json");
		jsonMarshaller.getObjectMapper().setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

		//Tweak existing XML RequestMarshaller
		JaxbRequestMarshaller requestMarshaller = (JaxbRequestMarshaller) sender.getRequestMarshaller("application/xml");
		//Set indented output
		requestMarshaller.getMarshallerProperties().put(Marshaller.JAXB_FORMATTED_OUTPUT, true);

		//Set own JSON RequestMarshaller
		//sender.setRequestMarshaller(new MyLovelyGsonMarshaller(), "application/json");

		//Tweak existing JSON ResponseExtractorFactory
		Jackson2ExtractorFactory jsonExtractorFactory = (Jackson2ExtractorFactory) sender
				.getResponseExtractorFactory("application/json");
		jsonExtractorFactory.getObjectMapper().setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

		sender.setResponseExtractorFactory(jsonExtractorFactory, "application/json");
		/*
		config.setAuthentication(Authentication.DIGEST("myusername", "mypassword"));
		sender = config.buildSender();
		ExtractedBodyResponse<String> extract3 = sender.GET("/digest-auth/auth/myusername/mypassword")
				.extract(String.class);
		System.out.println(extract3.getBody());
		sender.close();
		*/
	}

	/**
	 * CachingSender caches HTTP Responses. Response body/entity is kept as String or byte array.
	 * Any extract method execution will use cached Response, but actual "extraction" will be performed every time
	 */
	public static void cachingSender() {
		//Github uses ETag and Cache control headers nicely
		HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");
		//Provide cache instance - Simple Heap Hashmap in this case
		CacheBase<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		//Create caching sender
		CachingSender csender = new CachingSender(sender, cache);

		//Create normal request first
		GetRequest getusers = sender.GET("/users").param("since", 333).build();

		//Response will be cached for 1 minute. Only first request will reach github
		//All subsequent requests (with same parameters) will hit the cache 

		//2a Use fluent interface to execute/extract
		for (int i = 0; i < 1000; ++i) {
			ExtractedBodyResponse<String> extract = csender.request(getusers).hardTTL(1, TimeUnit.MINUTES)
					.extract(String.class);
			extract.getBody();//Cache hit
		}

		//2b Create CachingRequest - classic
		CachingRequest crequest1 = new CachingRequest(getusers, 1, TimeUnit.MINUTES);
		for (int i = 0; i < 1000; ++i) {
			ExtractedBodyResponse<String> response = csender.extract(crequest1, String.class);
			response.getBody();//Cache hit
		}

		//2c Create CachingRequest - fluent
		CachingRequest crequest2 = csender.request(getusers).hardTTL(1, TimeUnit.MINUTES).build();
		for (int i = 0; i < 1000; ++i) {
			ExtractedBodyResponse<String> response = csender.extract(crequest2, String.class);
			response.getBody();//Cache hit
		}

		sender.close();
		cache.close();
	}

	public static void cachingExtractor() {
		//Create normal HttpSender
		HttpClient4Sender sender = new HttpClient4Sender("http://httpbin.org");
		//Provide cache instance - Simple Heap Hashmap in this case
		CacheBase<Object> cache = new HeapMapCache<Object>();
		//Create Caching Extractor
		CachingExtractor cextractor = new CachingExtractor(sender, cache);

		//Create normal request
		GetRequest get = new GetRequest("/get");

		//Response will kept in cache for 10 seconds (hard TTL) and will be refreshed every 5 seconds (soft TTL) using background thread.

		//Use fluent interface to execute/extract
		for (int i = 0; i < 1000; ++i) {
			HttpbinOut out = cextractor.request(get).ttl(10, 5, TimeUnit.SECONDS).extract(HttpbinOut.class);//Cache hit
		}

		//Precreated Caching request
		CachingExtractorRequest<HttpbinOut> crequest = cextractor.request(get).ttl(10, 5, TimeUnit.SECONDS)
				.build(HttpbinOut.class);
		for (int i = 0; i < 1000; ++i) {
			HttpbinOut out = cextractor.extract(crequest); //Cache hit
		}

		//Precreated Caching request
		CachingExtractorRequest<HttpbinOut> crequest2 = new CachingExtractorRequest<HttpbinOut>(get, HttpbinOut.class, 10,
				5, TimeUnit.SECONDS, RefreshMode.REQUEST_SYNC);
		for (int i = 0; i < 1000; ++i) {
			HttpbinOut out = cextractor.extract(crequest2);//Cache hit
		}

		cache.close();
		cextractor.close();
	}

	public static void cachingScheduled() {
		//Create normal HttpSender
		HttpSender sender = new HttpClient4Sender("http://httpbin.org");
		//Provide cache instance - Simple Heap Hashmap in this case
		CacheBase<Object> cache = new HeapMapCache<Object>();
		//Setup asynchronous support
		ExecutorService executor = ExecutorServiceBuilder.builder().setMaximumPoolSize(1).setMaximumQueueSize(1).build();
		//Create Caching Extractor
		CachingExtractor cextractor = new CachingExtractor(sender, cache, executor);
		//Create normal request
		GetRequest get = new GetRequest("/get");

		//Response will kept in cache for 60 seconds (hatr TTL) and will be refreshed every 3 seconds (soft TTL) using background thread. 
		//Unavailability could happen only when remote service became unaccessible for more than 60-3 seconds
		CachingExtractorRequest<HttpbinOut> crequest = cextractor.request(get).ttl(60, 3, TimeUnit.SECONDS)
				.refresh(RefreshMode.SCHEDULED).build(HttpbinOut.class);

		HttpbinOut out1 = cextractor.extract(crequest); //cache put
		HttpbinOut out2 = cextractor.extract(crequest); //cache hit
		Assert.assertTrue(out1 == out2); //same instance from cache

		//sleep until background refresh is performed
		try {
			Thread.sleep(4 * 1000);
		} catch (InterruptedException ix) {
			ix.printStackTrace();
		}

		HttpbinOut out3 = cextractor.extract(crequest);
		Assert.assertTrue(out1 != out3); //different instance now

		sender.close();
		executor.shutdown();
		cache.close();
	}
}

class HttpbinIn {

	private String someString;

	private Date someDate;

	public String getSomeString() {
		return someString;
	}

	public void setSomeString(String someString) {
		this.someString = someString;
	}

	public Date getSomeDate() {
		return someDate;
	}

	public void setSomeDate(Date someDate) {
		this.someDate = someDate;
	}

}

class HttpbinOut {

	private String origin;

	private String url;

	private Map<String, String> files;

	private Map<String, String> form;

	private Map<String, String> headers;

	private Map<String, String> args;

	private String json;

	private String data;

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Map<String, String> getFiles() {
		return files;
	}

	public void setFiles(Map<String, String> files) {
		this.files = files;
	}

	public Map<String, String> getForm() {
		return form;
	}

	public void setForm(Map<String, String> form) {
		this.form = form;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public Map<String, String> getArgs() {
		return args;
	}

	public void setArgs(Map<String, String> args) {
		this.args = args;
	}

	public String getJson() {
		return json;
	}

	public void setJson(String json) {
		this.json = json;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

}
