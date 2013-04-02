package com.anthavio.hatatitla.example;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import com.anthavio.hatatitla.HttpClient4Config;
import com.anthavio.hatatitla.HttpClient4Sender;
import com.anthavio.hatatitla.inout.ResponseBodyExtractor.ExtractedBodyResponse;

/**
 * We will be using Github API http://developer.github.com/v3/
 * 
 * @author martin.vanek
 *
 */
public class ExamplesTest {

	public static void main(String[] args) {
		httpbin();
	}

	private static void github() {
		HttpClient4Config config = new HttpClient4Config("https://api.github.com");

		HttpClient4Sender sender = config.buildSender();
		try {
			ExtractedBodyResponse<String> extracted = sender.GET("/users/anthavio").extract(String.class);
			extracted.getResponse().getHttpStatusCode();
			System.out.println(extracted.getBody());
		} catch (IOException iox) {
			iox.printStackTrace();
		}
	}

	public static void httpbin() {
		HttpClient4Config config = new HttpClient4Config("http://httpbin.org");
		HttpClient4Sender sender = config.buildSender();
		try {
			ExtractedBodyResponse<HttpbinResponse> extract = sender.PUT("/put").param("p1", "v1")
					.body("{ x : 'y' }", "application/json").extract(HttpbinResponse.class);
			System.out.println(extract.getBody().getOrigin());
		} catch (IOException iox) {
			iox.printStackTrace();
		}
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
