Hatatitla
=========

Configurable and tweakable REST client library you have been dreaming of

* Both fluent & traditional (constructor/setter) request interface
* XML request/response payload support (JAXB)
* JSON request/response payload support (Jackson)
* Response caching (memory/ehcache/memcached)
* Response content negotiation (content-type, charset, gzip)
* Extensive configuration with reasonable defaults
* Connection pooling
* Asynchronous requests
* Pluggable transport layer - Apache Httpclient4 / Apache Httpclient3 / HttpURLConnection

Maven Repository
-------------

```xml
    <repository>
        <id>sonatype-oss-public</id>
        <url>https://oss.sonatype.org/content/groups/public/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
```

Maven coordinates
-------------

```xml
    <dependency>
        <groupId>com.anthavio</groupId>
        <artifactId>hatatitla</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
```

Fluent API
-------------

```java
		//Create sender with utf-8 encoding, default timeouts and connection pool
		HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");

		ExtractedBodyResponse<String> extracted1 = sender.GET("/users").param("since", 333).extract(String.class);
		//Just print unprocessed JSON String
		System.out.println(extracted1.getBody());

		//Free connection pool
		sender.close();
```

Traditional API
-------------

```java
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
```

Request/Response marshalling
-------------

For XML bodies, standard JAXB used for marshalling requests and responses and no additional library is required.

For JSON bodies, Jackson 1 or Jackson 2 must be present on classpath, otherwise following exception will occur.

```
java.lang.IllegalArgumentException: Request body marshaller not found for application/json
or
java.lang.IllegalArgumentException: No extractor factory found for mime type application/json
```
Java beans representing bodies must be existing. In this example, HttpbinIn and HttpbinOut are model beans.

```java
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
```

Configuration
-------------

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
		config.setGzipRequest(true); //default is false

		//What to do with null or "" parameters?
		config.setNullValueStrategy(ValueStrategy.SKIP); //default is KEEP
		config.setEmptyValueStrategy(ValueStrategy.SKIP); //default is KEEP

		//Tired of setting Accept Header to every request?
		config.setDefaultAccept("application/json"); //default is none

		HttpClient4Sender sender = config.buildSender();
		//...send send send...
		sender.close();
```

Sender Implementations
-------------

### URLHttpSender - Easy to start with

```java
		//No additional dependency - vanilla java
		URLHttpConfig urlConfig = new URLHttpConfig("https://graph.facebook.com");
		URLHttpSender urlSender = urlConfig.buildSender();
```

### HttpClient4Sender - Recommended choice

```java		
		HttpClient4Config http4config = new HttpClient4Config("https://api.twitter.com");
		HttpClient4Sender http4sender = http4config.buildSender();
```
Dependency - http://hc.apache.org/httpcomponents-client-ga/

```xml
		<dependency>
			<groupId>org.apache.httpcomponents</groupId>
			<artifactId>httpclient</artifactId>
			<version>4.2.3</version>
		</dependency>
```

Missing dependency - java.lang.NoClassDefFoundError: org/apache/http/client/methods/HttpRequestBase

### HttpClient3Sender - Legacy choice

```java
		HttpClient3Config http3config = new HttpClient3Config("https://api.twitter.com");
		HttpClient3Sender http3sender = http3config.buildSender();
```
Dependency - http://hc.apache.org/httpclient-3.x/

```xml
		<dependency>
			<groupId>commons-httpclient</groupId>
			<artifactId>commons-httpclient</artifactId>
			<version>3.1</version>
		</dependency>
```
Missing dependency - java.lang.NoClassDefFoundError: org/apache/commons/httpclient/HttpMethodBase
