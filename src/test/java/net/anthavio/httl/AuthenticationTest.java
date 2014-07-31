package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;

import net.anthavio.httl.impl.HttpClient3Config;
import net.anthavio.httl.impl.HttpClient4Config;
import net.anthavio.httl.impl.HttpUrlConfig;
import net.anthavio.httl.impl.JettySenderConfig;

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
	public void simple() throws IOException {

		System.setProperty("http.keepAlive", "false");

		String url = "http://localhost:" + server.getHttpPort();
		HttlSender sender = new HttpUrlConfig(url).build();

		HttlResponse response = sender.GET("/").param("x", "y").execute();
		response.close();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = sender.GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		sender.close();

		//NOW setup BASIC authentication
		HttpUrlConfig config = new HttpUrlConfig(url);
		//authentication.setPreemptive(false);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		basic.setRealm("MyBasicRealm");
		config.setAuthentication(basic);

		sender = config.build();
		response = sender.GET("/basic/").execute();
		response.close();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender.close();

		sender = config.build();
		response = sender.GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka

		sender.close();

		//NOW setup DIGEST authentication
		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		basic.setRealm("MyDigestRealm");
		config.setAuthentication(digest);

		sender = config.build();
		response = sender.GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		sender.close();

		sender = config.build();
		response = sender.GET("/digest/").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender.close();
	}

	@Test
	public void http3() throws IOException {
		String url = "http://localhost:" + this.server.getHttpPort();
		HttlSender sender = new HttpClient3Config(url).build();

		HttlResponse response = sender.GET("/").param("x", "y").execute();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/basic/").execute();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = sender.GET("/digest/").execute();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		sender.close();

		//setup BASIC authentication

		HttpClient3Config config = new HttpClient3Config(url);
		Authentication authentication = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(authentication);
		sender = config.build();

		response = sender.GET("/basic/").execute();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/digest/").execute();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED); //incorrect role for lajka

		sender.close();

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);
		sender = config.build();

		response = sender.GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);//BASIC credentials does NOT work on DIGEST resource

		response = sender.GET("/digest/").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender.close();
	}

	@Test
	public void http4() throws IOException {

		String url = "http://localhost:" + this.server.getHttpPort();

		HttlSender sender = new HttpClient4Config(url).build();
		HttlResponse response = sender.GET("/").param("x", "y").execute();
		response.close();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response.close();

		response = sender.GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpClient4Config config = new HttpClient4Config(url);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(basic);

		response = config.build().GET("/basic/").execute();
		response.close();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = config.build().GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);

		response = config.build().GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = config.build().GET("/digest/").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender.close();
	}

	@Test
	public void jetty() throws IOException {

		String url = "http://localhost:" + this.server.getHttpPort();
		HttlSender sender = new JettySenderConfig(url).build();

		HttlResponse response = sender.GET("/").param("x", "y").execute();
		response.close();
		//can access unprotected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = sender.GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = sender.GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		JettySenderConfig config = new JettySenderConfig(url);
		Authentication basic = new Authentication(Authentication.Scheme.BASIC, "lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(basic);

		response = config.build().GET("/basic/").execute();
		response.close();
		//can access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		response = config.build().GET("/digest/").execute();
		response.close();
		//can't access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "zora", "stekystek");
		config.setAuthentication(digest);

		response = config.build().GET("/basic/").execute();
		response.close();
		//can't access BASIC protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		response = config.build().GET("/digest/").execute();
		response.close();
		//can access DIGEST protected
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

}
