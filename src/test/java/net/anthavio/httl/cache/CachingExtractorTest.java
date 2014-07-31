package net.anthavio.httl.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.cache.CacheLoadRequest;
import net.anthavio.cache.HeapMapCache;
import net.anthavio.cache.Scheduler;
import net.anthavio.httl.HttlException;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.JokerServer;
import net.anthavio.httl.HttlRequestBuilders.SenderRequestBuilder;
import net.anthavio.httl.ResponseExtractor;
import net.anthavio.httl.ResponseExtractor.ExtractedResponse;
import net.anthavio.httl.ResponseStatusException;
import net.anthavio.httl.async.ExecutorServiceBuilder;
import net.anthavio.httl.impl.HttpClient4Config;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * FIXME fix tests
 * 
 * @author martin.vanek
 *
 */
public abstract class CachingExtractorTest {

	private JokerServer server;
	private ThreadPoolExecutor executor;

	@Before
	public void setup() throws Exception {
		this.server = new JokerServer().start();
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@After
	public void destroy() throws Exception {
		this.executor.shutdown();
		this.server.stop();
	}

	@Test
	public void syncRefreshExtraction() throws Exception {
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());

		HttlRequest request = cextractor.getSender().GET("/").build();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		//soft 1s, hard 2s, synchronous refresh
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(extractor, request, 2, 1,
				TimeUnit.SECONDS);

		final int initialCount = server.getRequestCount();

		//first request goes to the server
		CacheEntry<String> extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(501);
		//taken from cache
		CacheEntry<String> extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2).isEqualTo(extract1); //same

		Thread.sleep(501); //after soft expiration
		//taken from server because ve have synchronous updates
		CacheEntry<String> extract3 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //new
		assertThat(extract3).isNotEqualTo(extract1); //different

		//now with errors - soft expired response is returned even if server refresh call fails

		//set server to return errors - simulate server error
		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

		Thread.sleep(1010); //after soft expiration

		try {
			cextractor.extract(cerequest); //sync refresh will fail
			Assert.fail("Preceding line must throw SenderHttpException");
			//CacheEntry<String> extract4 = cextractor.extract(cerequest); //sync refresh will fail
			//assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //new
			//assertThat(extract4).isEqualTo(extract3); //same expired from cache
		} catch (ResponseStatusException srx) {
			//this is what we expect
		}

		Thread.sleep(1010); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw SenderHttpException");
		} catch (ResponseStatusException srx) {
			//this is what we expect
		}
		//error returned but request was made 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 4); //new

		//set server to return ok again
		server.setHttpCode(HttpURLConnection.HTTP_OK);

		//make successful request again
		CacheEntry<String> extract5 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //new
		assertThat(extract5).isNotEqualTo(extract3); //different

		server.stop(); //stop server - simulate server outage

		Thread.sleep(1001); //after soft expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw ConnectException");
		} catch (HttlException sex) {
			//this is what we expect
			assertThat(sex.getMessage()).contains("Connection refused"); //same
		}

		//CacheEntry<String> extract6 = cextractor.extract(cerequest);
		//assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //same
		//assertThat(extract6).isEqualTo(extract5); //same from cache

		Thread.sleep(1001); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw ConnectException");
		} catch (HttlException sex) {
			//this is what we expect
			assertThat(sex.getMessage()).contains("Connection refused"); //same
		}
		//cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	@Test
	public void backgroundRefreshExtraction() throws Exception {
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());

		HttlRequest request = cextractor.getSender().GET("/").param("sleep", 1).build();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;

		CachingExtractorRequest<String> cerequest = cextractor.from(request).cache(2, 1, TimeUnit.SECONDS)
				.async(true, true).build(extractor);

		CacheLoadRequest<String> cacheRequest = cextractor.convert(cerequest);
		CacheBase<Serializable> cache = cextractor.getCache();
		cache.schedule(cacheRequest);
		//.schedule(cacheRequest);

		final int initialCount = server.getRequestCount();
		assertThat(cextractor.extract(cerequest)).isNull(); //scheduled is null returer
		Thread.sleep(1200);//async refresh
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		CacheEntry<String> extract1 = cextractor.extract(cerequest);
		assertThat(extract1.isStale() == false);

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //background refresh server hit
		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //background refresh server hit

		long m1 = System.currentTimeMillis();
		CacheEntry<String> extract2 = cextractor.extract(cerequest);
		assertThat(extract2.isStale() == false);
		assertThat(System.currentTimeMillis() - m1).isLessThan(10);//from cache - must be quick!
		assertThat(extract2.getValue()).isNotEqualTo(extract1.getValue()); //different

		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
		Thread.sleep(5010); //after hard expiry + server sleep

		assertThat(cextractor.extract(cerequest)).isNull(); //scheduled is null returer
		//cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	private void schedule(CacheLoadRequest<ExtractedResponse<String>> cacheRequest) {
		// TODO Auto-generated method stub

	}

	@Test
	public void asyncRefreshExtraction() throws Exception {

		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		HttlRequest request = cextractor.getSender().GET("/").build();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		CachingExtractorRequest<String> cerequest = cextractor.from(request).cache(2, 1, TimeUnit.SECONDS)
				.async(true, true).build(extractor); //asynchronous updates!

		final int initialCount = server.getRequestCount();

		assertThat(cextractor.extract(cerequest)).isNull(); //async is null returer
		Thread.sleep(200); //async http refresh
		CacheEntry<String> extract1 = cextractor.extract(cerequest); //cached now
		assertThat(extract1.isStale() == false);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(1001); //after soft expiration

		//taken from cache
		CacheEntry<String> extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2.isStale());
		assertThat(extract2.getValue()).isEqualTo(extract1.getValue()); //same

		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //plus 1

		//from cache again - but different value
		CacheEntry<String> extract3 = cextractor.extract(cerequest);
		Thread.sleep(100); //wait for async thread NOT to hit the server 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2);//same
		assertThat(extract3.getValue()).isNotEqualTo(extract2.getValue()); //updated in cache asyncronously

		//now with errors - soft expired response is returned even if server refresh call fails

		//set server to return errors - simulate server error
		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

		Thread.sleep(1010); //after soft expiration

		CacheEntry<String> extract4 = cextractor.extract(cerequest); //async refresh will fail
		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //new
		assertThat(extract4.getValue()).isEqualTo(extract3.getValue()); //same expired from cache

		Thread.sleep(1010); //after hard expiration
		assertThat(cextractor.extract(cerequest)).isNull(); //async is null returer
		Thread.sleep(200); //async refresh
		//error returned but request was made 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 4); //new

		//set server to return ok again
		server.setHttpCode(HttpURLConnection.HTTP_OK);
		assertThat(cextractor.extract(cerequest)).isNull(); //async is null returer
		Thread.sleep(200);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //new

		//make successful request again
		CacheEntry<String> extract5 = cextractor.extract(cerequest);
		assertThat(extract5.getValue()).isNotEqualTo(extract4.getValue()); //different

		server.stop(); //stop server - simulate server outage

		Thread.sleep(1001); //after soft expiration
		CacheEntry<String> extract6 = cextractor.extract(cerequest);
		Thread.sleep(100); //wait for async thread NOT to hit the server 
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 5); //same
		assertThat(extract6).isEqualTo(extract5); //same from cache

		Thread.sleep(1001); //after hard expiration
		assertThat(cextractor.extract(cerequest)).isNull(); //async is null returer

		//cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	@Test
	public void asyncNonDuplicateRefreshOnSameResourceExtraction() throws Exception {

		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequestBuilder<?> builder = cextractor.getSender().GET("/");
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		CachingExtractorRequest<String> cerequest = cextractor.from(builder.build()).cache(2, 1, TimeUnit.SECONDS)
				.async(true, true).build(extractor); //asynchronous updates!

		//request must spent some time in server
		builder.param("sleep", 1);
		cerequest = cextractor.from(builder.build()).cache(2, 1, TimeUnit.SECONDS).async(true, true).build(extractor);

		final int initialCount = server.getRequestCount();

		assertThat(cextractor.extract(cerequest)).isNull(); //null returner
		Thread.sleep(1200); //async refresh with server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		CacheEntry<String> extract1 = cextractor.extract(cerequest);
		assertThat(extract1.isStale() == false);

		Thread.sleep(1001); //after soft expiry

		assertThat(executor.getActiveCount()).isEqualTo(0);
		CacheEntry<String> extract2 = cextractor.extract(cerequest); //this request should start async update
		assertThat(extract2.getValue()).isEqualTo(extract1.getValue()); //soft expired response is returned immediately

		Thread.sleep(100); //let async refresh hit the server

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // plus 1
		//next 900 (1000-100) millis same request will be discarded because of running refresh

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		CacheEntry<String> extract3 = cextractor.extract(cerequest); //discarded request 
		assertThat(extract3).isEqualTo(extract1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // same as before
		assertThat(executor.getActiveCount()).isEqualTo(1); //refresh is still there
		assertThat(executor.getQueue().size()).isEqualTo(0);

		Thread.sleep(1000); //refresh thread finished

		assertThat(executor.getActiveCount()).isEqualTo(0);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		CacheEntry<String> extract4 = cextractor.extract(cerequest); //from cache 
		assertThat(extract4).isNotEqualTo(extract3);

		//cextractor.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	private CachingExtractor newExtractorSender(int port) {
		String url = "http://localhost:" + port;
		//HttpSender sender = new JavaHttpSender(url);
		HttlSender sender = new HttpClient4Config(url).build();
		HeapMapCache<Serializable> cache = new HeapMapCache<Serializable>();
		Scheduler<Serializable> scheduler = new Scheduler<Serializable>(cache, executor);
		cache.setScheduler(scheduler);
		CachingExtractor csender = new CachingExtractor(sender, cache);

		return csender;
	}
}
