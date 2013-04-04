package com.anthavio.hatatitla.example;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import com.anthavio.hatatitla.Authentication;
import com.anthavio.hatatitla.GetRequest;
import com.anthavio.hatatitla.HttpClient3Config;
import com.anthavio.hatatitla.HttpClient3Sender;
import com.anthavio.hatatitla.HttpClient4Config;
import com.anthavio.hatatitla.HttpClient4Sender;
import com.anthavio.hatatitla.SenderRequest.ValueStrategy;
import com.anthavio.hatatitla.URLHttpConfig;
import com.anthavio.hatatitla.URLHttpSender;
import com.anthavio.hatatitla.inout.Jackson2RequestMarshaller;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * We will be using Github API http://developer.github.com/v3/
 * 
 * @author martin.vanek
 *
 */
public class ExamplesTest {

	public static void main(String[] args) {
		binding();
	}

	public static void fluent() {
		//Create sender with utf-8 encoding, default timeouts and connection pool
		HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");

		ExtractedBodyResponse<String> extracted1 = sender.GET("/users").param("since", 666).extract(String.class);
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
		request.setParameter("since", 666);
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
		URLHttpConfig urlConfig = new URLHttpConfig("https://graph.facebook.com");
		URLHttpSender urlSender = urlConfig.buildSender();

		//Recommended choice
		//Dependency - http://hc.apache.org/httpcomponents-client-ga/
		HttpClient4Config http4config = new HttpClient4Config("https://api.twitter.com");
		HttpClient4Sender http4sender = http4config.buildSender();

		//Legacy choice
		//Dependency - http://hc.apache.org/httpclient-3.x/
		HttpClient3Config http3config = new HttpClient3Config("https://api.twitter.com");
		HttpClient3Sender http3sender = http3config.buildSender();
	}

	public static void binding() {
		HttpClient4Config config = new HttpClient4Config("http://httpbin.org");
		HttpClient4Sender sender = new HttpClient4Sender(config);

		Jackson2RequestMarshaller marshaller = (Jackson2RequestMarshaller) sender.getRequestMarshaller("application/json");
		marshaller.getObjectMapper().setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

		HttpbinIn binIn = new HttpbinIn();
		binIn.setSomeDate(new Date());
		binIn.setSomeString("Hello!");

		ExtractedBodyResponse<HttpbinOut> extract = sender.PUT("/put").body(binIn, "application/json")
				.extract(HttpbinOut.class);
		System.out.println(extract.getBody().getOrigin());
		sender.close();
		/*
		config.setAuthentication(Authentication.DIGEST("myusername", "mypassword"));
		sender = config.buildSender();
		ExtractedBodyResponse<String> extract3 = sender.GET("/digest-auth/auth/myusername/mypassword")
				.extract(String.class);
		System.out.println(extract3.getBody());
		sender.close();
		*/
	}

	static class HttpbinIn {

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

	static class HttpbinOut implements Serializable {

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

}
