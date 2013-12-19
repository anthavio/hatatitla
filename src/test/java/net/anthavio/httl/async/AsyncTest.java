package net.anthavio.httl.async;

import static org.fest.assertions.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.anthavio.httl.GetRequest;
import net.anthavio.httl.HttpClient4Sender;
import net.anthavio.httl.JokerServer;
import net.anthavio.httl.SenderException;
import net.anthavio.httl.SenderHttpStatusException;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.async.ExecutorServiceBuilder;
import net.anthavio.httl.inout.ResponseBodyExtractors;
import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;

import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.junit.Assert;
import org.testng.annotations.Test;


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
			HttpClient4Sender sender = new HttpClient4Sender("localhost:" + server.getHttpPort(), executor);

			PoolingClientConnectionManager conman = (PoolingClientConnectionManager) sender.getHttpClient()
					.getConnectionManager();

			server.stop(); //stop server

			SenderRequest request = new GetRequest("/").addParameter("sleep", 1);

			Future<SenderResponse> future = sender.start(request);
			try {
				future.get();
				Assert.fail("Previous statement must throw ExecutionException");
			} catch (ExecutionException ex) {
				assertThat(ex.getCause()).isInstanceOf(SenderException.class);
				assertThat(ex.getCause().getMessage()).contains("Connection refused");
				//ex.printStackTrace();
			}
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0);

			sender.close();

			server.start();
			sender = new HttpClient4Sender("localhost:" + server.getHttpPort(), executor);
			conman = (PoolingClientConnectionManager) sender.getHttpClient().getConnectionManager();

			Future<SenderResponse> future2 = sender.start(request);
			try {
				future2.get(550, TimeUnit.MILLISECONDS); //server sleep is 1 second!
				Assert.fail("Previous statement must throw TimeoutException");
			} catch (TimeoutException tx) {
				//this is expected
			}
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(1); //request is still in progress
			Thread.sleep(600); //after server complete request
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0); //response is cached and closed
			SenderResponse response2 = future2.get();
			assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

			server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

			//start execution
			Future<SenderResponse> future3 = sender.start(request);
			SenderResponse response3 = future3.get(1100, TimeUnit.MILLISECONDS); //server sleep is 1 second!
			assertThat(conman.getTotalStats().getLeased()).isEqualTo(0); //response is cached and closed
			assertThat(response3.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR); //no exception, byt error response response

			//start extraction
			Future<ExtractedBodyResponse<String>> future4 = sender.start(request, ResponseBodyExtractors.STRING);
			try {
				future4.get();
				Assert.fail("Previous statement must throw SenderHttpException");
			} catch (ExecutionException ex) {
				assertThat(ex.getCause()).isInstanceOf(SenderHttpStatusException.class);
			}

			server.setHttpCode(HttpURLConnection.HTTP_OK);
			Future<ExtractedBodyResponse<String>> future5 = sender.start(request, ResponseBodyExtractors.STRING);
			ExtractedBodyResponse<String> response5 = future5.get();
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
