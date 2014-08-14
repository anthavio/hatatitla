package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.HttlRequestBuilders.SenderNobodyRequestBuilder;
import net.anthavio.httl.async.ExecutorServiceBuilder;
import net.anthavio.httl.transport.HttpClient3Config;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpUrlConfig;
import net.anthavio.httl.transport.JettySenderConfig;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class TimeoutsTest {

	private static JokerServer server = new JokerServer();

	private static String urlSingle;

	private static String urlFrozen;

	private static ExecutorService executor;

	@BeforeClass
	public static void setup() throws Exception {
		server.start();
		urlSingle = "http://localhost:" + server.getHttpPort() + "/";
		//this.urlFrozen = "http://localhost:" + this.server.getFrozenPort() + "/";
		//this.urlFrozen = "http://www.google.com:81/";
		urlFrozen = "http://10.254.254.254/";

		executor = new ExecutorServiceBuilder().build();
	}

	@AfterClass
	public static void destroy() throws Exception {
		server.stop();
	}

	@Test
	public void simple() throws IOException {
		connectTimeout(newSimple(urlFrozen));

		//pool timeout is not testable here
		//poolTimeout(newSimple(this.urlSingle));

		HttlSender sender = newSimple(urlSingle);
		readTimeout(sender);
	}

	@Test
	public void http3() throws IOException {
		connectTimeout(newHttp3(urlFrozen));

		poolTimeout(newHttp3(urlSingle));

		readTimeout(newHttp3(urlSingle));
	}

	@Test
	public void http4() throws IOException {
		connectTimeout(newHttp4(urlFrozen));

		poolTimeout(newHttp4(urlSingle));

		readTimeout(newHttp4(urlSingle));
	}

	//@Test //Buggy as hell
	public void jetty() throws IOException {
		HttlSender sender = newJetty(urlFrozen);
		connectTimeout(sender);
		sender.close();

		//pool timeout is not testable here
		//poolTimeout(newSimple(this.urlSingle));

		sender = newJetty(urlSingle);
		readTimeout(sender);
		sender.close();
	}

	private void poolTimeout(HttlSender sender) throws IOException {
		HttlRequest request = sender.GET("/").param("sleep", "1").build();
		//get only existing connection from pool
		sender.start(request);
		//sleep to be sure that conenction will be leased
		sleep(100);
		//second should fail on pool exception
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw ConnectException");
		} catch (HttlException sex) {
			//cx.printStackTrace();
			assertThat(sex.getMessage()).isEqualTo("java.net.ConnectException: Pool timeout 300 ms");
		}
		//sleep 
		sender.close();
	}

	private void connectTimeout(HttlSender sender) throws IOException {
		HttlRequest request = sender.GET("/").build();
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw ConnectException");
		} catch (HttlException sex) {
			//if (isMacOs()) {
			assertThat(sex.getMessage()).isEqualTo("java.net.ConnectException: Connect timeout 1100 ms");
			//} else {
			//	assertThat(sex.getMessage()).isEqualTo("java.net.SocketTimeoutException: Read timeout 1300 ms");
			//}
		}
		sender.close();
	}

	//private boolean isMacOs() {
	//	return System.getProperty("os.name").toLowerCase().contains("mac");
	//}

	private void readTimeout(HttlSender sender) throws IOException {
		SenderNobodyRequestBuilder builder = sender.GET("/");
		//pass without sleep

		HttlResponse response = builder.execute();
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close(); //return to pool

		//timeout with sleep
		builder.param("sleep", "2");
		try {
			builder.execute();
			Assert.fail("Previous statement must throw SocketTimeoutException");
		} catch (HttlException sex) {
			//stx.printStackTrace();
			assertThat(sex.getMessage()).isEqualTo("java.net.SocketTimeoutException: Read timeout 1300 ms");
		}

		//override configuration value
		builder.timeout(900, TimeUnit.MILLISECONDS);
		HttlRequest request = builder.build();
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw SocketTimeoutException");
		} catch (HttlException sex) {
			//stx.printStackTrace();
			assertThat(sex.getMessage()).isEqualTo("java.net.SocketTimeoutException: Read timeout 900 ms");
		}

		sender.close();
	}

	private static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ix) {
			throw new IllegalStateException("Somebody interrupted us!", ix);
		}
	}

	private HttlSender newSimple(String url) {
		HttpUrlConfig config = new HttpUrlConfig(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		System.setProperty("http.keepAlive", "true");
		System.setProperty("http.maxConnections", "1");
		config.setExecutorService(executor);
		return config.build();
	}

	private HttlSender newHttp3(String url) {
		HttpClient3Config config = new HttpClient3Config(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		config.setPoolAcquireTimeoutMillis(300);
		config.setExecutorService(executor);
		return config.build();
	}

	private HttlSender newHttp4(String url) {
		HttpClient4Config config = new HttpClient4Config(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		config.setPoolAcquireTimeoutMillis(300);
		config.setExecutorService(executor);
		return config.build();
	}

	private HttlSender newJetty(String url) {
		JettySenderConfig config = new JettySenderConfig(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		//config.setPoolAcquireTimeoutMillis(300);
		config.setExecutorService(executor);
		return config.build();
	}

}
