package com.anthavio.httl.cache;

import static org.fest.assertions.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.anthavio.httl.GetRequest;
import com.anthavio.httl.HttpClient4Sender;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.JokerServer;
import com.anthavio.httl.SenderException;
import com.anthavio.httl.SenderHttpStatusException;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.async.ExecutorServiceBuilder;
import com.anthavio.httl.cache.CachingRequest.RefreshMode;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractors;

/**
 * 
 * @author martin.vanek
 *
 */
public class ExtractionTest {

	private ThreadPoolExecutor executor;

	@BeforeClass
	public void setup() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.executor.shutdown();
	}

	@Test
	public void syncRefreshExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());

		SenderRequest request = new GetRequest("/");
		ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		//soft 1s, hard 2s, synchronous refresh
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS);

		final int initialCount = server.getRequestCount();

		//first request goes to the server
		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(501);
		//taken from cache
		String extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2).isEqualTo(extract1); //same

		Thread.sleep(501); //after soft expiration
		//taken from server because ve have synchronous updates
		String extract3 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //new
		assertThat(extract3).isNotEqualTo(extract1); //different

		//now with errors - soft expired response is returned even if server refresh call fails

		//set server to return errors - simulate server error
		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

		Thread.sleep(1010); //after soft expiration

		String extract4 = cextractor.extract(cerequest); //sync refresh will fail
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //new
		assertThat(extract4).isEqualTo(extract3); //same expired from cache

		Thread.sleep(1010); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw SenderHttpException");
		} catch (SenderHttpStatusException srx) {
			//this is what we expect
		}
		//error returned but request was made 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 4); //new

		//set server to return ok again
		server.setHttpCode(HttpURLConnection.HTTP_OK);

		//make successful request again
		String extract5 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //new
		assertThat(extract5).isNotEqualTo(extract4); //different

		server.stop(); //stop server - simulate server outage

		Thread.sleep(1001); //after soft expiration
		String extract6 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //same
		assertThat(extract6).isEqualTo(extract5); //same from cache

		Thread.sleep(1001); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw ConnectException");
		} catch (SenderException sex) {
			//this is what we expect
			assertThat(sex.getMessage()).contains("Connection refused"); //same
		}
		cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	@Test
	public void backgroundRefreshExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());

		SenderRequest request = new GetRequest("/").addParameter("sleep", 1);
		ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS, RefreshMode.SCHEDULED); //automatic updates!

		final int initialCount = server.getRequestCount();
		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //background refresh server hit
		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //background refresh server hit

		long m1 = System.currentTimeMillis();
		String extract2 = cextractor.extract(cerequest);
		assertThat(System.currentTimeMillis() - m1).isLessThan(10);//from cache - must be quick!
		assertThat(extract2).isNotEqualTo(extract1); //different

		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
		Thread.sleep(5010); //after hard expiry + server sleep

		try {
			cextractor.extract(cerequest);
			Assert.fail("Previous statement must throw SenderHttpException");
		} catch (SenderHttpStatusException srx) {
			//this is what we expect
		}

		cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	@Test
	public void asyncRefreshExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequest request = new GetRequest("/");
		ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS, RefreshMode.REQUEST_ASYN); //asynchronous updates!

		final int initialCount = server.getRequestCount();

		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(1001); //after soft expiration

		//taken from cache
		String extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2).isEqualTo(extract1); //same

		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //plus 1

		//from cache again - but different value
		String extract3 = cextractor.extract(cerequest);
		Thread.sleep(100); //wait for async thread NOT to hit the server 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2);//same
		assertThat(extract3).isNotEqualTo(extract2); //updated in cache asyncronously

		//now with errors - soft expired response is returned even if server refresh call fails

		//set server to return errors - simulate server error
		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

		Thread.sleep(1010); //after soft expiration

		String extract4 = cextractor.extract(cerequest); //async refresh will fail
		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //new
		assertThat(extract4).isEqualTo(extract3); //same expired from cache

		Thread.sleep(1010); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw SenderHttpException");
		} catch (SenderHttpStatusException srx) {
			//this is what we expect
		}
		//error returned but request was made 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 4); //new

		//set server to return ok again
		server.setHttpCode(HttpURLConnection.HTTP_OK);

		//make successful request again
		String extract5 = cextractor.extract(cerequest);
		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //new
		assertThat(extract5).isNotEqualTo(extract4); //different

		server.stop(); //stop server - simulate server outage

		Thread.sleep(1001); //after soft expiration
		String extract6 = cextractor.extract(cerequest);
		Thread.sleep(100); //wait for async thread NOT to hit the server 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //same
		assertThat(extract6).isEqualTo(extract5); //same from cache

		Thread.sleep(1001); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw ConnectException");
		} catch (SenderException sex) {
			//this is what we expect
			assertThat(sex.getMessage()).contains("Connection refused"); //same
		}

		cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	@Test
	public void asyncNonDuplicateRefreshOnSameResourceExtraction() throws Exception {

		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequest request = new GetRequest("/");
		ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS, RefreshMode.REQUEST_ASYN); //asynchronous updates!

		//request must spent some time in server
		cerequest.getSenderRequest().addParameter("sleep", 1);

		final int initialCount = server.getRequestCount();

		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);
		Thread.sleep(1001); //after soft expiry

		assertThat(executor.getActiveCount()).isEqualTo(0);
		String extract2 = cextractor.extract(cerequest); //this request should start async update
		assertThat(extract2).isEqualTo(extract1); //soft expired response is returned immediately

		Thread.sleep(100); //let async refresh hit the server

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // plus 1
		//next 900 (1000-100) millis same request will be discarded because of running refresh

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		String extract3 = cextractor.extract(cerequest); //discarded request 
		assertThat(extract3).isEqualTo(extract1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // same as before
		assertThat(executor.getActiveCount()).isEqualTo(1); //refresh is still there
		assertThat(executor.getQueue().size()).isEqualTo(0);

		Thread.sleep(1000); //refresh thread finished

		assertThat(executor.getActiveCount()).isEqualTo(0);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		String extract4 = cextractor.extract(cerequest); //from cache 
		assertThat(extract4).isNotEqualTo(extract3);

		cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	private CachingExtractor newExtractorSender(int port) {
		String url = "http://localhost:" + port;
		//HttpSender sender = new JavaHttpSender(url);
		HttpSender sender = new HttpClient4Sender(url);
		HeapMapRequestCache<Object> cache = new HeapMapRequestCache<Object>();
		CachingExtractor csender = new CachingExtractor(sender, cache);
		csender.setExecutor(executor);
		return csender;
	}
}
