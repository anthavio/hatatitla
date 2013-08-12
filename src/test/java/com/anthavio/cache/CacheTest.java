package com.anthavio.cache;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.spy.memcached.MemcachedClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.cache.CacheEntryLoader.BaseCacheLoader;
import com.anthavio.cache.CacheEntryLoader.CacheLoaderException;
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

	private CacheManager ehCacheManager;

	@BeforeMethod
	public void beforeMethod() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@AfterMethod
	public void afterMathod() throws Exception {
		this.executor.shutdown();
	}

	@AfterClass
	public void shutdown() throws Exception {

		if (memcached != null && memcached.isRunning()) {
			memcached.stop();
		}

		if (ehCacheManager != null) {
			ehCacheManager.shutdown();
		}
	}

	@Test
	public void testSimpleCache() throws Exception {
		CacheBase<String> cache = new HeapMapCache<String>();
		doCacheTest(cache);
	}

	@Test
	public void testEhCache() throws Exception {
		CacheBase<String> cache;
		cache = buildEhCache();
		//cache = buildMemcache();
		doCacheTest(cache);
	}

	@Test
	public void testMemcached() throws Exception {
		CacheBase<String> cache;
		cache = buildMemcache();
		doCacheTest(cache);
	}

	/**
	 * Basic caching operations we expect to work with any cache implementation
	 */
	private void doCacheTest(CacheBase<String> cache) throws InterruptedException, IOException {

		String cacheKey = String.valueOf(System.currentTimeMillis());
		//hard ttl is 2 seconds
		CacheEntry<String> centry = new CacheEntry<String>("CachedValue", 2, 1);
		Boolean added = cache.set(cacheKey, new CacheEntry<String>("CachedValue", 2, 1));
		assertThat(added).isTrue();

		CacheEntry<String> entry = cache.get(cacheKey);
		assertThat(entry.getValue()).isEqualTo(centry.getValue());
		assertThat(entry.isSoftExpired()).isFalse();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(1010); //after soft ttl

		entry = cache.get(cacheKey);
		assertThat(entry.getValue()).isEqualTo(centry.getValue());
		assertThat(entry.isSoftExpired()).isTrue();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(2010); //after hard ttl + 1 second (memcached has this whole second precision)

		entry = cache.get(cacheKey);
		assertThat(entry).isNull();

		//add & remove & get
		added = cache.set(cacheKey, new CacheEntry<String>("CachedValue", 2, 1));
		assertThat(added).isTrue();

		Boolean removed = cache.remove(cacheKey);
		assertThat(removed).isTrue();

		entry = cache.get(cacheKey);
		assertThat(entry).isNull();

		cache.close();
	}

	@Test
	public void testBlockingMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		//no need for executor in blocking mode

		TestLoader fetch = new TestLoader();
		CacheRequest<String> req = new CacheRequest<String>("Block", fetch, 2, 1, TimeUnit.SECONDS, RefreshMode.BLOCK);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start
		try {
			cache.get(req);
			Assert.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isInstanceOf(IllegalStateException.class);
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
			Assert.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isInstanceOf(IllegalStateException.class);
		}
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired
		try {
			cache.get(req);
			Assert.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isInstanceOf(IllegalStateException.class);
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
		cache.close();
	}

	/**
	 * Return mode throws Exception only when cache miss and fetcher error
	 */
	@Test
	public void testReturnMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		TestLoader fetch = new TestLoader();
		CacheRequest<String> req = new CacheRequest<String>("Return", fetch, 2, 1, TimeUnit.SECONDS, RefreshMode.RETURN);

		int rCounter = 0;
		fetch.setThrowException(true); // break it from the start
		try {
			cache.get(req);
			Assert.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isInstanceOf(IllegalStateException.class);
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
			Assert.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isInstanceOf(IllegalStateException.class);
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
		cache.close();
	}

	/**
	 * Async never throws Exceptions but returns null
	 */
	@Test
	public void testAsyncRefresh() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		TestLoader fetch = new TestLoader();
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
		cache.close();
	}

	/**
	 * Dodgy test because scheduler thread is doin cache checks in another thread every second 
	 */
	@Test
	public void testScheduled() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		cache.setExecutor(executor);

		TestLoader fetch = new TestLoader();
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
		cache.close();
	}

	/**
	 * 
	 
	@Test
	public void testLoaderContract() {

		final Logger logger = LoggerFactory.getLogger(getClass());

		BaseCacheLoader<String> loader = new BaseCacheLoader<String>() {

			public String result;

			@Override
			protected Logger getLogger() {
				return logger;
			}

			@Override
			protected String doLoad(CacheRequest<String> request, CacheEntry<String> softExpired) throws Exception {
				return request.getUserKey();
			}
		};
		loader.l
	}
	*/

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
		SpyMemcache<String> cache = new SpyMemcache<String>("CacheTest", client, 1, TimeUnit.SECONDS);
		return cache;
	}

	private EHCache<String> buildEhCache() {
		if (ehCacheManager == null) {
			ehCacheManager = CacheManager.create();
			Cache ehCache = new Cache("EHCache", 5000, false, false, 0, 0);
			ehCacheManager.addCache(ehCache);
		}
		EHCache<String> cache = new EHCache<String>("EHCache", ehCacheManager.getCache("EHCache"));
		return cache;
	}

	private static class TestLoader extends BaseCacheLoader<String> {

		private Logger logger = LoggerFactory.getLogger(getClass());

		private AtomicInteger rCounter = new AtomicInteger(0);

		private String lastValue = null;

		private boolean throwException = false;

		public void setThrowException(boolean throwException) {
			this.throwException = throwException;
		}

		@Override
		public String doLoad(CacheRequest<String> request, CacheEntry<String> softExpiredEntry) {
			rCounter.incrementAndGet();
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
			return rCounter.get();
		}

		@Override
		protected Logger getLogger() {
			return logger;
		}

	};
}
