package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.async.ExecutorServiceBuilder;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class TimeoutsTest {

	private JokerServer server = new JokerServer();

	private String urlSingle;

	private String urlFrozen;

	private ExecutorService executor;

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
		this.urlSingle = "http://localhost:" + this.server.getHttpPort() + "/";
		//this.urlFrozen = "http://localhost:" + this.server.getFrozenPort() + "/";
		//this.urlFrozen = "http://www.google.com:81/";
		this.urlFrozen = "http://10.254.254.254/";

		this.executor = new ExecutorServiceBuilder().build();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();
	}

	@Test
	public void simple() throws IOException {
		connectTimeout(newSimple(this.urlFrozen));

		//pool timeout is not testable here
		//poolTimeout(newSimple(this.urlSingle));

		HttpSender sender = newSimple(this.urlSingle);
		readTimeout(sender);
	}

	@Test
	public void http3() throws IOException {
		connectTimeout(newHttp3(this.urlFrozen));

		poolTimeout(newHttp3(this.urlSingle));

		readTimeout(newHttp3(this.urlSingle));
	}

	@Test
	public void http4() throws IOException {
		connectTimeout(newHttp4(this.urlFrozen));

		poolTimeout(newHttp4(this.urlSingle));

		readTimeout(newHttp4(this.urlSingle));
	}

	//@Test //Buggy as hell
	public void jetty() throws IOException {
		HttpSender sender = newJetty(this.urlFrozen);
		connectTimeout(sender);
		sender.close();

		//pool timeout is not testable here
		//poolTimeout(newSimple(this.urlSingle));

		sender = newJetty(this.urlSingle);
		readTimeout(sender);
		sender.close();
	}

	private void poolTimeout(HttpSender sender) throws IOException {
		GetRequest request = new GetRequest("/");
		request.addParameter("sleep", "1");
		//get only existing connection from pool
		sender.start(request);
		//sleep to be sure that conenction will be leased
		sleep(100);
		//second should fail on pool exception
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw ConnectException");
		} catch (SenderException sex) {
			//cx.printStackTrace();
			assertThat(sex.getMessage()).isEqualTo("java.net.ConnectException: Pool timeout 300 ms");
		}
		//sleep 
		sender.close();
	}

	private void connectTimeout(HttpSender sender) throws IOException {
		GetRequest request = new GetRequest("/");
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw ConnectException");
		} catch (SenderException sex) {
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

	private void readTimeout(HttpSender sender) throws IOException {
		SenderRequest request = new GetRequest("/");
		//pass without sleep

		SenderResponse response = sender.execute(request);
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response.close(); //return to pool

		//timeout with sleep
		request.addParameter("sleep", "2");
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw SocketTimeoutException");
		} catch (SenderException sex) {
			//stx.printStackTrace();
			assertThat(sex.getMessage()).isEqualTo("java.net.SocketTimeoutException: Read timeout 1300 ms");
		}

		//override configuration value
		request.setReadTimeout(900, TimeUnit.MILLISECONDS);
		try {
			sender.execute(request);
			Assert.fail("Previous statement must throw SocketTimeoutException");
		} catch (SenderException sex) {
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

	private HttpURLSender newSimple(String url) {
		HttpURLConfig config = new HttpURLConfig(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		System.setProperty("http.keepAlive", "true");
		System.setProperty("http.maxConnections", "1");
		HttpURLSender sender = new HttpURLSender(config);
		sender.setExecutor(executor);
		return sender;
	}

	private HttpClient3Sender newHttp3(String url) {
		HttpClient3Config config = new HttpClient3Config(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		config.setPoolAcquireTimeoutMillis(300);
		HttpClient3Sender sender = new HttpClient3Sender(config);
		sender.setExecutor(executor);
		return sender;
	}

	private HttpClient4Sender newHttp4(String url) {
		HttpClient4Config config = new HttpClient4Config(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		config.setPoolAcquireTimeoutMillis(300);
		HttpClient4Sender sender = new HttpClient4Sender(config);
		sender.setExecutor(executor);
		return sender;
	}

	private JettySender newJetty(String url) {
		JettySenderConfig config = new JettySenderConfig(url);
		config.setConnectTimeoutMillis(1100);
		config.setReadTimeoutMillis(1300);
		config.setPoolMaximumSize(1);
		//config.setPoolAcquireTimeoutMillis(300);
		JettySender sender = new JettySender(config);
		sender.setExecutor(executor);
		return sender;
	}

}
