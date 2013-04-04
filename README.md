Hatatitla
=========

Configurable and tweakable REST client library you have been dreaming of

* Both fluent & traditional (constructor/setter) request interface
* XML (JAXB) request/response payload support
* JSON (Jackson) request/response payload support
* Response caching (memory/ehcache/memcached)
* Response content negotiation (content-type and charset)
* Extensive configuration with reasonable defaults
* Connection pooling
* Asynchronous requests
* Pluggable transport layer Apache Httpclient 4/Apache Httpclient 3/HttpURLConnection

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
Examples
-------------
Simplest example ever - JSON string from github API
```java
        //Create sender with utf-8 encoding, default timeouts and connection pool
        HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");
	    
        //Fluent builder (sleek eye candy)
	    ExtractedBodyResponse<String> extracted1 = sender.GET("/users").param("since", 666).extract(String.class);
		System.out.println(extracted1.getBody());
        
		//Traditional (dependency injection friendly)
		GetRequest get = new GetRequest("/users");
		get.setParam("since", 666);
		//Sender must be used to execute/extract
		ExtractedBodyResponse<String> extracted2 = sender.extract(get, String.class);
		System.out.println(extracted2.getBody());
```
