package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;

import net.anthavio.httl.transport.HttpClient3Config;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpUrlConfig;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class AuthenticationTest {

	private static JokerServer server = new JokerServer();

	@BeforeClass
	public static void setup() throws Exception {
		server.start();
	}

	@AfterClass
	public static void destroy() throws Exception {
		server.stop();
	}

	@Test
	public void javaHttpUrlSender() throws IOException {

		System.setProperty("http.keepAlive", "false");

		//Given - sender without authentication
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = HttlSender.url(url).build();

		//When 
		HttlResponse response = sender.GET("/").param("x", "y").execute();
		//Then - OK
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		sender.close();

		//Given - HttlSender with BASIC credentials

		HttpUrlConfig config = new HttpUrlConfig(url);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!")
				.setRealm("MyBasicRealm");
		config.setAuthentication(basic);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - allowed
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();
		sender.close();

		sender = config.sender().build();
		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka
		response.close();

		sender.close();

		//Given - HttlSender with DIGEST credentials

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek")
				.setRealm("MyDigestRealm");
		config.setAuthentication(digest);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		sender.close();
		sender = config.sender().build();
		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - allowed
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();
		sender.close();
	}

	@Test
	public void httpClient3() throws IOException {

		//Given - sender without authentication
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = new HttpClient3Config(url).sender().build();

		//When - unprotected
		HttlResponse response = sender.GET("/").param("x", "y").execute();
		//Then - allow
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401 can't access 
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401 can't access 
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		sender.close();

		//Given - setup BASIC authentication

		HttpClient3Config config = new HttpClient3Config(url);
		Authentication authentication = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		config.setAuthentication(authentication);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - allowed
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka
		response.close();

		sender.close();

		//Given - setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);//BASIC credentials does NOT work on DIGEST resource
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - OK
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		sender.close();
	}

	@Test
	public void httpClient4() throws IOException {

		//Given - sender without authentication
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = new HttpClient4Config(url).sender().build();

		//When - unprotected
		HttlResponse response = sender.GET("/").param("x", "y").execute();
		//Then - allow
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401 can't access 
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401 can't access 
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
		response.close();

		sender.close();

		//Given - setup BASIC authentication

		HttpClient4Config config = new HttpClient4Config(url);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		config.setAuthentication(basic);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - allowed
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka
		response.close();

		sender.close();

		//Given - setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);
		sender = config.sender().build();

		//When - BASIC protected resource
		response = sender.GET("/basic/").execute();
		//Then - 401
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);//BASIC credentials does NOT work on DIGEST resource
		response.close();

		//When - DIGEST protected resource
		response = sender.GET("/digest/").execute();
		//Then - OK
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close();

		sender.close();
	}
	/*
		@Test
		public void jetty() throws IOException {

			//Given - sender without authentication
			String url = "http://localhost:" + server.getPortHttp();
			HttlSender sender = new JettyClientConfig(url).sender().build();

			//When - unprotected
			HttlResponse response = sender.GET("/").param("x", "y").execute();
			//Then - allow
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
			response.close();

			//When - BASIC protected resource
			response = sender.GET("/basic/").execute();
			//Then - 401 can't access 
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
			response.close();

			//When - DIGEST protected resource
			response = sender.GET("/digest/").execute();
			//Then - 401 can't access 
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
			response.close();

			//Given - setup BASIC authentication

			JettyClientConfig config = new JettyClientConfig(url);
			Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
			config.setAuthentication(basic);
			sender = config.sender().build();

			//When - BASIC protected resource
			response = sender.GET("/basic/").execute();
			//Then - allowed
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
			response.close();

			//When - DIGEST protected resource
			response = sender.GET("/digest/").execute();
			//Then - 401
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka
			response.close();

			sender.close();

			//Given - setup DIGEST authentication

			Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
			config.setAuthentication(digest);
			sender = config.sender().build();

			//When - BASIC protected resource
			response = sender.GET("/basic/").execute();
			//Then - 401
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);//BASIC credentials does NOT work on DIGEST resource
			response.close();

			//When - DIGEST protected resource
			response = sender.GET("/digest/").execute();
			//Then - OK
			assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
			response.close();
		}
	*/
}
