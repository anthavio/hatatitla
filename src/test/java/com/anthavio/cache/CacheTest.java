package com.anthavio.cache;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.spy.memcached.MemcachedClient;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.cache.CacheRequest.CacheEntryFetch;
import com.anthavio.cache.CacheRequest.FetchResult;
import com.anthavio.httl.async.ExecutorServiceBuilder;
import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheTest {

	private ThreadPoolExecutor executor;

	private MemCacheDaemon<LocalCacheElement> memcached;

	private static class Fetcher implements CacheEntryFetch<String> {

		private AtomicInteger rCounter = new AtomicInteger(0);

		private String lastValue = null;

		private boolean throwException = false;

		public void setThrowException(boolean throwException) {
			this.throwException = throwException;
		}

		@Override
		public FetchResult<String> fetch(CacheRequest<String> request, CacheEntry<String> softExpiredEntry) {
			rCounter.incrementAndGet();
			lastValue = new SimpleDateFormat("dd.MM.yyyy'T'HH:mm:ss.SSS").format(new Date());
			if (throwException) {
				throw new IllegalStateException("I'm baaad. I'm baaad.");
			}
			return new FetchResult<String>(lastValue, true);
		}

		public String getLastValue() {
			return lastValue;
		}

		public int getRequestCount() {
			return rCounter.get();
		}

	};

	@BeforeClass
	public void setup() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.executor.shutdown();

		if (memcached != null && memcached.isRunning()) {
			memcached.stop();
		}
	}

	@Test
	public void testBlockingMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		//no need for executor in blocking mode

		Fetcher fetch = new Fetcher();
		CacheRequest<String> req = new CacheRequest<String>("Block", fetch, 2, 1, TimeUnit.SECONDS, RefreshMode.BLOCK);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start
		try {
			cache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setThrowException(false); //let updater fetch entry into cache
		CacheEntry<String> entry1 = cache.get(req);
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(entry1.isSoftExpired() == false);
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500);//entry still fresh
		fetch.setThrowException(true); //broke updater again
		CacheEntry<String> entry2 = cache.get(req);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue()); //get from cache
		assertThat(entry2.isSoftExpired() == false);
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired
		try {
			cache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired
		try {
			cache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setThrowException(false); //ok be nice again

		CacheEntry<String> entry5 = cache.get(req);
		assertThat(entry5.getValue()).isNotNull();
		assertThat(entry5.isSoftExpired() == false);
		assertThat(entry5.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500); //entry still fresh
		CacheEntry<String> entry6 = cache.get(req);
		assertThat(entry6.getValue()).isEqualTo(entry5.getValue()); //same value from cache
		assertThat(entry6.isSoftExpired());
		assertThat(entry6.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired now
		CacheEntry<String> entry7 = cache.get(req);
		assertThat(entry7.getValue()).isNotEqualTo(entry6.getValue()); //new value is fetched
		assertThat(entry7.isSoftExpired() == false);
		assertThat(entry7.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
	}

	/**
	 * Return mode throws Eception only when cache miss and fetcher error
	 */
	@Test
	public void testReturnMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		Fetcher fetch = new Fetcher();
		CacheRequest<String> req = new CacheRequest<String>("Return", fetch, 2, 1, TimeUnit.SECONDS, RefreshMode.RETURN);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start
		try {
			cache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setThrowException(false); //let updater fetch entry into cache
		CacheEntry<String> entry1 = cache.get(req);
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(entry1.isSoftExpired() == false);
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500);//entry still fresh
		fetch.setThrowException(true); //broke updater again
		CacheEntry<String> entry2 = cache.get(req);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue()); //get from cache
		assertThat(entry2.isSoftExpired() == false);
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired
		CacheEntry<String> entry3 = cache.get(req);
		Thread.sleep(50); //complete async fetch
		assertThat(entry3.getValue()).isEqualTo(entry1.getValue()); //get soft expired from cache and log error //XXX how to check error log?
		assertThat(entry3.isSoftExpired());
		assertThat(entry3.getValue()).isNotEqualTo(fetch.getLastValue());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired
		try {
			cache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setThrowException(false); //ok be nice again

		CacheEntry<String> entry5 = cache.get(req);
		assertThat(entry5.getValue()).isNotNull();
		assertThat(entry5.isSoftExpired() == false);
		assertThat(entry5.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500); //entry still fresh
		CacheEntry<String> entry6 = cache.get(req);
		assertThat(entry6.getValue()).isEqualTo(entry5.getValue()); //same value from cache
		assertThat(entry6.isSoftExpired() == false);
		assertThat(entry6.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired now
		CacheEntry<String> entry7 = cache.get(req);
		assertThat(entry7.isSoftExpired());
		assertThat(entry7.getValue()).isEqualTo(entry5.getValue()); //same value from cache
		assertThat(entry7.getValue()).isEqualTo(fetch.getLastValue());

		Thread.sleep(50); //complete async fetch
		assertThat(entry7.getValue()).isNotEqualTo(fetch.getLastValue());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
		//entry is fresh again
		CacheEntry<String> entry8 = cache.get(req);
		assertThat(entry8.isSoftExpired() == false);
		assertThat(entry8.getValue()).isNotEqualTo(entry5.getValue()); //new value fetched
		assertThat(entry8.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());
	}

	/**
	 * Async never throws Exceptions but returns null
	 */
	@Test
	public void testAsyncRefresh() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		Fetcher fetch = new Fetcher();
		CacheRequest<String> req = new CacheRequest<String>("Async", fetch, 2, 1, TimeUnit.SECONDS, RefreshMode.ASYNC);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start

		assertThat(cache.get(req)).isNull(); //async is null returner
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setThrowException(false); //let updater fetch entry into cache

		assertThat(cache.get(req)).isNull(); //still null
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
		CacheEntry<String> entry1 = cache.get(req);
		assertThat(entry1.isSoftExpired() == false);
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastValue());

		fetch.setThrowException(true); //broke updater again

		Thread.sleep(500);//entry still fresh
		CacheEntry<String> entry2 = cache.get(req);
		assertThat(entry2.isSoftExpired() == false);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue()); //get from cache
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastValue());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired now
		CacheEntry<String> entry3 = cache.get(req);
		assertThat(entry3.isSoftExpired());//return soft expired - refresh is asynchronous
		assertThat(entry3.getValue()).isEqualTo(entry1.getValue());

		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired

		assertThat(cache.get(req)).isNull(); //async is null returner
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setThrowException(false); //be nice again

		assertThat(cache.get(req)).isNull(); //async is null returner
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry4 = cache.get(req);
		assertThat(entry4.isSoftExpired() == false);
		assertThat(entry4.getValue()).isEqualTo(fetch.getLastValue()); // new value in cache
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired now
		CacheEntry<String> entry5 = cache.get(req);
		assertThat(entry5.isSoftExpired());//return soft expired - refresh is asynchronous
		assertThat(entry5.getValue()).isEqualTo(entry4.getValue());

		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry6 = cache.get(req);//cache is refreshed
		assertThat(entry6.isSoftExpired() == false);
		assertThat(entry6.getValue()).isNotEqualTo(entry4.getValue());
	}

	/**
	 * Dodgy test because scheduler thread is doin cache checks in another thread every second 
	 */
	@Test
	public void testScheduled() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		Fetcher fetch = new Fetcher();
		CacheRequest<String> req = new CacheRequest<String>("Scheduled", fetch, 2, 1, TimeUnit.SECONDS,
				RefreshMode.SCHEDULED);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start

		assertThat(cache.get(req)).isNull(); //null returner
		assertThat(cache.getScheduled().size()).isEqualTo(1);

		Thread.sleep(50); //scheduled refresh done (fail)
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
		assertThat(cache.get(req)).isNull(); //null returner

		Thread.sleep(1000);//scheduled refresh done (fail)
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
		assertThat(cache.get(req)).isNull(); //null returner

		fetch.setThrowException(false); // be nice now

		Thread.sleep(1000);//scheduled refresh done (pass)
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry1 = cache.get(req);//cache is refreshed
		assertThat(entry1.isSoftExpired() == false);
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastValue());

		assertThat(cache.getScheduled().size()).isEqualTo(1); //resubmitted but still only one
		//cache.close();
	}

	private SpyMemcache<String> buildMemcache() throws IOException {
		InetSocketAddress address = new InetSocketAddress(11311);
		if (memcached == null) {
			memcached = new MemCacheDaemon<LocalCacheElement>();

			int maxItems = 5000;
			long maxBytes = 10 * 1024 * 1024;
			CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(
					ConcurrentLinkedHashMap.EvictionPolicy.FIFO, maxItems, maxBytes);
			memcached.setCache(new CacheImpl(storage));
			memcached.setBinary(false);
			memcached.setAddr(address);
			memcached.setIdleTime(1000);
			memcached.setVerbose(true);
			memcached.start();
		}

		MemcachedClient client = new MemcachedClient(address);
		SpyMemcache<String> cache = new SpyMemcache<String>("whatever", client, 1, TimeUnit.SECONDS);
		return cache;
	}

}
