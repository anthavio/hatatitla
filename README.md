Hatatitla [![Build Status](https://vanek.ci.cloudbees.com/buildStatus/icon?job=hatatitla-snapshot)](https://vanek.ci.cloudbees.com/job/hatatitla-snapshot/) [![Coverage Status](https://coveralls.io/repos/anthavio/hatatitla/badge.png?branch=master)](https://coveralls.io/r/anthavio/hatatitla?branch=master)
==========
 ![Cloudbees DEV@cloud](http://www.cloudbees.com/sites/default/files/Button-Powered-by-CB.png)


Configurable and tweakable REST client library

* Cool fluent interface
* XML request/response payload support (JAXB)
* JSON request/response payload support (Jackson)
* Response caching (memory/ehcache/memcached)
* Response content negotiation (content-type, charset, gzip)
* Extensive configuration with reasonable defaults
* Connection pooling - multithreaded by default
* Asynchronous requests - java Future and ExecutorService infrastructure
* Multiple transport options - Apache Httpclient4 / Apache Httpclient3 / HttpURLConnection

Maven Repository & coordinates
-------------

```xml
    <dependency>
        <groupId>net.anthavio</groupId>
        <artifactId>hatatitla</artifactId>
        <version>1.5.0</version>
    </dependency>
```

Fluent API
-------------
Fluent buiders pattern is used for complex request creation and execution

```java
		//Create sender with utf-8 encoding, default timeouts and connection pool
		HttlSender sender = HttlSender.url("https://api.github.com").build();

		ExtractedResponse<String> extracted = sender.GET("/users").param("since", 333).extract(String.class);
		//Just print unprocessed JSON String
		System.out.println(extracted.getBody());

		//Free connection pool
		sender.close();
```


Request/Response Body marshalling
-------------

For XML payloads, standard JAXB used for marshalling requests and responses and no additional library is required.

For JSON payloads, Jackson 1 or Jackson 2 must be present on classpath, otherwise following exception will occur.

```
net.anthavio.httl.HttlRequestException: Marshaller not found for application/json
```
Java beans representing bodies must be existing. In this example, HttpbinIn and HttpbinOut are model beans.

```java
		HttpClient4Sender sender = new HttpClient4Sender("http://httpbin.org");

		//Send HttpbinIn instance marshalled as JSON document
		HttpbinIn binIn = new HttpbinIn();
		binIn.setSomeDate(new Date());
		binIn.setSomeString("Hello!");

		//Using extract method will parse returned Httpbin JSON document into HttpbinOut instance
		ExtractedResponse<HttpbinOut> extract = sender.POST("/post").body(binIn, "application/json").extract(HttpbinOut.class);

		HttpbinOut body = extract.getBody(); //voila!

		sender.close();
```

Configuration
-------------

Hatatitla Sender is extensively configurable with reasonable default values.

```java
		HttpClient4Config config = new HttpClient4Config("http://httpbin.org");

		//That pesky IIS is using Cyrillic? No problem!
		config.setEncoding("Cp1251"); //default utf-8

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

		//Tired of setting Accept Header to every request?
		SenderConfigurer configurer = config.sender();
		configurer.setResponseMediaType("application/json"); //default is none

		//How to treat null or "" parameter values?
		boolean keepNullParams = false;
		boolean keepEmptyParams = true;
		boolean urlEncodeNames = false;
		boolean urlEncodeValues = true;
		String dateParamPattern = "dd-MM-yyyy";
		ConfigurableParamSetter paramSetter = new ConfigurableParamSetter(keepNullParams, keepEmptyParams, urlEncodeNames, urlEncodeValues, dateParamPattern);

		configurer.setParamSetter(paramSetter);

		HttlSender sender = configurer.build();
		//...send send send...
		sender.close();
```


Caching
-------------
Hatatitla can cache responses or extracted bodies of responses. Cached data can be refreshed synchronously or asynchronously during request or scheduled to be updated in the background.

In memory, EHCache and SpyMemcache caches are available out of the box. Other implementation can be easily created by extending RequestCache class.

```java
		//Create normal HttpSender
		HttpClient4Sender sender = new HttpClient4Sender("http://httpbin.org");
		//Provide cache instance - Simple Heap Hashmap in this case
		RequestCache<Object> cache = new HeapMapRequestCache<Object>();
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
```

Example of the automatic response refresh/update

```java
		//Create normal HttpSender
		HttpSender sender = new HttpClient4Sender("http://httpbin.org");
		//Provide cache instance - Simple Heap Hashmap in this case
		RequestCache<Object> cache = new HeapMapRequestCache<Object>();
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
```



Sender Implementations
-------------

### URLHttpSender - Easy to start with

```java
		//No additional dependency - vanilla java
		HttlSender urlSender = HttlSender.url("https://graph.facebook.com").build();
```

### HttpClient4Sender - Recommended choice

```java		
		HttpClient4Config http4config = HttlSender.url("https://api.twitter.com").httpClient4();
		HttlSender http4sender = http4config.sender().build();
```
Dependency - http://hc.apache.org/httpcomponents-client-ga/

```xml
		<dependency>
			<groupId>org.apache.httpcomponents</groupId>
			<artifactId>httpclient</artifactId>
			<version>4.2.3</version>
		</dependency>
```

Missing dependency - java.lang.IllegalStateException: HttClient 4 classes not found in classpath

### HttpClient3Sender - Legacy choice

```java
		HttpClient3Config http3config = HttlBuilder.httpClient3("https://api.twitter.com");
		HttlSender http3sender = http3config.sender().build();
```
Dependency - http://hc.apache.org/httpclient-3.x/

```xml
		<dependency>
			<groupId>commons-httpclient</groupId>
			<artifactId>commons-httpclient</artifactId>
			<version>3.1</version>
		</dependency>
```
Missing dependency - java.lang.IllegalStateException: HttClient 3.1 classes not found in classpath
