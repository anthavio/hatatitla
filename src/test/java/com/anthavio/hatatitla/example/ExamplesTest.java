package com.anthavio.hatatitla.example;

import java.io.Serializable;
import java.util.Map;

import com.anthavio.hatatitla.Authentication;
import com.anthavio.hatatitla.GetRequest;
import com.anthavio.hatatitla.HttpClient4Sender;
import com.anthavio.hatatitla.URLHttpSender;
import com.anthavio.hatatitla.URLSenderConfig;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * We will be using Github API http://developer.github.com/v3/
 * 
 * @author martin.vanek
 *
 */
public class ExamplesTest {

	public static void main(String[] args) {
		github();
	}

	private static void github() {

		//Create sender with utf-8 encoding, default timeouts and connection pool
		HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com");
		//Fluent builder way of request construction
		ExtractedBodyResponse<String> extracted = sender.GET("/users/anthavio").extract(String.class);
		System.out.println(extracted.getBody());
		//Traditional way (dependency injection friendly)

		//ExtractedBodyResponse<String> extracted = sender.GET("/users").param("since", 666).extract(String.class);
		System.out.println(extracted.getBody());

		//Request can be created independently on sender
		GetRequest get = new GetRequest("/users/anthavio");
		get.setParam("since", 666);
		//But then, Sender must be used to execute/extract it
		//ExtractedBodyResponse<String> extracted2 = sender.extract(get, String.class);
		//System.out.println(extracted2.getBody());

	}

	public static void httpbin() {
		URLSenderConfig config = new URLSenderConfig("http://httpbin.org");
		URLHttpSender sender = config.buildSender();
		ExtractedBodyResponse<String> extract2 = sender.GET("/gzip").extract(String.class);
		System.out.println(extract2.getBody());

		ExtractedBodyResponse<HttpbinResponse> extract = sender.PUT("/put").param("queryp1", "qv1")
				.param("matrixp1", "mv1").body("{ x : 'y' }", "application/json").extract(HttpbinResponse.class);
		System.out.println(extract.getBody().getOrigin());
		sender.close();

		config.setAuthentication(Authentication.DIGEST("myusername", "mypassword"));
		sender = config.buildSender();
		ExtractedBodyResponse<String> extract3 = sender.GET("/digest-auth/auth/myusername/mypassword")
				.extract(String.class);
		System.out.println(extract3.getBody());
		sender.close();
	}

	static class HttpbinResponse implements Serializable {

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
