package com.anthavio.hatatitla;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class AuthenticationTest {

	private JokerServer server = new JokerServer();

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();
	}

	//@Test
	public void simple() throws IOException {

		System.setProperty("http.keepAlive", "false");

		String url = "http://localhost:" + this.server.getHttpPort();
		URLHttpSender sender = new URLHttpSender(url);

		SenderResponse response = sender.GET("/").param("x", "y").execute();
		response.close();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/basic").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = sender.GET("/digest").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//NOW setup BASIC authentication
		URLSenderConfig config = new URLSenderConfig(url);
		//authentication.setPreemptive(false);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		config.setAuthentication(basic);

		sender = new URLHttpSender(config);
		response = sender.GET("/basic").execute();
		response.close();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender = new URLHttpSender(config);
		response = sender.GET("/digest").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN); //incorrect role for lajka

		//NOW setup DIGEST authentication
		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);

		sender = new URLHttpSender(config);
		response = sender.GET("/basic").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		sender = new URLHttpSender(config);
		response = sender.GET("/digest").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

	//@Test
	public void http3() throws IOException {
		SenderRequest request = new GetRequest("/");
		request.addParameter("x", "y");

		String url = "http://localhost:" + this.server.getHttpPort();
		SenderResponse response = new HttpClient3Sender(url).execute(request);
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/basic");
		response = new HttpClient3Sender(url).execute(request);
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(url).execute(request);
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpClient3Config config = new HttpClient3Config(url);
		Authentication authentication = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(authentication);

		request = new GetRequest("/basic");
		response = new HttpClient3Sender(config).execute(request);
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(config).execute(request);
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN); //incorrect role for lajka

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);

		request = new GetRequest("/basic");
		response = new HttpClient3Sender(config).execute(request);
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);//BASIC credentials does NOT work on DIGEST resource

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(config).execute(request);
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

	@Test
	public void http4() throws IOException {

		String url = "http://localhost:" + this.server.getHttpPort();
		SenderResponse response = new HttpClient4Sender(url).GET("/").param("x", "y").execute();
		response.close();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = new HttpClient4Sender(url).GET("/basic").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = new HttpClient4Sender(url).GET("/digest").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpClient4Config config = new HttpClient4Config(url);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!", "NoRealm");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(basic);

		response = config.buildSender().GET("/basic").execute();
		response.close();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = config.buildSender().GET("/digest").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);

		response = config.buildSender().GET("/basic").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = config.buildSender().GET("/digest").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

}
