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
import com.anthavio.cache.CacheRequest.CacheEntryUpdater;
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

	private static class Updater implements CacheEntryUpdater<String> {

		private AtomicInteger counter = new AtomicInteger(0);

		private String lastValue = null;

		private boolean throwException = false;

		public void setThrowException(boolean throwException) {
			this.throwException = throwException;
		}

		@Override
		public String fetch(CacheRequest<String> request) {
			counter.incrementAndGet();
			lastValue = new SimpleDateFormat("dd.MM.yyyy'T'HH:mm:ss.SSS").format(new Date());
			if (throwException) {
				throw new IllegalStateException("I'm baaad. I'm baaad.");
			}
			return lastValue;
		}

		public String getLastValue() {
			return lastValue;
		}

		public int getRequestCount() {
			return counter.get();
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
	public void testSyncRefresh() throws Exception {
		SpyMemcache<String> memcache = buildMemcache();

		Updater updater = new Updater();
		CacheRequest<String> req = new CacheRequest<String>("MyKey", updater, 2, 1, TimeUnit.SECONDS,
				RefreshMode.REQUEST_SYNC);

		int requestCounter = 0;
		updater.setThrowException(true);
		try {
			memcache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);

		updater.setThrowException(false); //let update to fetch entry into cache
		CacheEntry<String> entry1 = memcache.get(req);
		assertThat(entry1.getValue()).isEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);

		updater.setThrowException(true); //broke updater
		Thread.sleep(500);
		CacheEntry<String> entry2 = memcache.get(req);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue()); //get from cache
		assertThat(entry2.getValue()).isEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(requestCounter);

		Thread.sleep(1000); //soft expired now
		CacheEntry<String> entry3 = memcache.get(req);
		assertThat(entry3.getValue()).isEqualTo(entry1.getValue()); //get soft expired from cache and log error
		assertThat(entry3.getValue()).isNotEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);

		Thread.sleep(2000); //hard expired now
		try {
			memcache.get(req);
			Assert.fail("IllegalStateException must be thrown by previous statement");
		} catch (IllegalStateException isx) {
			//we want this
		}
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);

		updater.setThrowException(false); //be nice again

		CacheEntry<String> entry5 = memcache.get(req);
		assertThat(entry5.getValue()).isNotNull();
		assertThat(entry5.getValue()).isEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);

		Thread.sleep(500);
		CacheEntry<String> entry6 = memcache.get(req);
		assertThat(entry6.getValue()).isEqualTo(entry5.getValue()); //same value from cache
		assertThat(entry6.getValue()).isEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(requestCounter);

		Thread.sleep(1000); //soft expired now
		CacheEntry<String> entry7 = memcache.get(req);
		assertThat(entry7.getValue()).isNotEqualTo(entry6.getValue()); //new value is fetched
		assertThat(entry7.getValue()).isEqualTo(updater.getLastValue());
		assertThat(updater.getRequestCount()).isEqualTo(++requestCounter);
	}

	//@Test
	public void testAsyncRefresh() throws Exception {
		SpyMemcache<String> memcache = buildMemcache();

		Updater updater = new Updater();
		CacheRequest<String> req = new CacheRequest<String>("MyKey", updater, 2, 1, TimeUnit.SECONDS,
				RefreshMode.REQUEST_ASYN);
		CacheEntry<String> entry1 = memcache.get(req);
		Thread.sleep(500);
		CacheEntry<String> entry2 = memcache.get(req);
		assertThat(entry1.getValue()).isEqualTo(entry2.getValue());
		Thread.sleep(1000); //soft expired now
		CacheEntry<String> entry3 = memcache.get(req);
		assertThat(entry1.getValue()).isNotEqualTo(entry3.getValue());
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
