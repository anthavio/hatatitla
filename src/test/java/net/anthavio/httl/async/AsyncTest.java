package net.anthavio.httl.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.anthavio.httl.HttlException;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlStatusException;
import net.anthavio.httl.JokerServer;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.transport.HttpClient4Transport;

import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class AsyncTest {

	@Test
	public void test() throws Exception {
		JokerServer server = new JokerServer();
		try {
			server.start();

			ThreadPoolExecutor executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0)
					.setMaximumPoolSize(1).setMaximumQueueSize(0).build();
			HttpClient4Config config = new HttpClient4Config("localhost:" + server.getPortHttp());
			HttlSender sender = config.sender().setExecutorService(executor).build();

			HttpClient4Transport transport = (HttpClient4Transport) sender.getTransport();
			PoolingClientConnectionManager conman = (PoolingClientConnectionManager) transport.getHttpClient()
					.getConnectionManager();

			server.stop(); //stop server

			HttlRequest request = sender.GET("/").param("sleep", 1).build();

			Future<HttlResponse> future = sender.start(request);
			try {
				future.get();
				Assert.fail("Previous statement must throw ExecutionException");
			} catch (ExecutionException ex) {
				assertThat(ex.getCause()).isInstanceOf(HttlException.class);
				assertThat(ex.getCause().getMessage()).contains("Connection refused");
				//ex.printStackTrace();
			}
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0);

			sender.close();

			server.start();
			config = new HttpClient4Config("localhost:" + server.getPortHttp());
			sender = config.sender().setExecutorService(executor).build();

			transport = (HttpClient4Transport) sender.getTransport();
			conman = (PoolingClientConnectionManager) transport.getHttpClient().getConnectionManager();

			Future<HttlResponse> future2 = sender.start(request);
			try {
				future2.get(550, TimeUnit.MILLISECONDS); //server sleep is 1 second!
				Assert.fail("Previous statement must throw TimeoutException");
			} catch (TimeoutException tx) {
				//this is expected
			}
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(1); //request is still in progress
			Thread.sleep(600); //after server complete request
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0); //response is cached and closed
			HttlResponse response2 = future2.get();
			assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

			server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

			//start execution
			Future<HttlResponse> future3 = sender.start(request);
			HttlResponse response3 = future3.get(1100, TimeUnit.MILLISECONDS); //server sleep is 1 second!
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0); //response is cached and closed
			assertThat(response3.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR); //no exception, byt error response response

			//start extraction
			Future<ExtractedResponse<String>> future4 = sender.start(request, String.class);
			try {
				future4.get();
				Assert.fail("Previous statement must throw SenderHttpException");
			} catch (ExecutionException ex) {
				assertThat(ex.getCause()).isInstanceOf(HttlStatusException.class);
			}

			server.setHttpCode(HttpURLConnection.HTTP_OK);
			Future<ExtractedResponse<String>> future5 = sender.start(request, String.class);
			ExtractedResponse<String> response5 = future5.get();
			assertThat(response5.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

			//end conditions are all 0
			assertThat(executor.getActiveCount()).isEqualTo(0);
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0);
			sender.close();

		} finally {
			server.stop();
		}
	}
}
