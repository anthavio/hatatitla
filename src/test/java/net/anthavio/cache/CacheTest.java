package net.anthavio.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.ServerException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.anthavio.cache.CacheEntryLoader.CacheLoaderException;
import net.anthavio.cache.ConfiguredCacheLoader.CacheReturned;
import net.anthavio.cache.ConfiguredCacheLoader.ExpiredFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.ExpiredReturn;
import net.anthavio.cache.ConfiguredCacheLoader.LogErrorAs;
import net.anthavio.cache.ConfiguredCacheLoader.MissingFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.MissingReturn;
import net.anthavio.cache.ConfiguredCacheLoader.SimpleLoader;
import net.anthavio.httl.async.ExecutorServiceBuilder;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.spy.memcached.MemcachedClient;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static MemCacheDaemon<LocalCacheElement> memcached;

	private static CacheManager ehCacheManager;

	private static Exception exception = new ServerException("I'm baaad! I'm very intentionaly baaad!");

	@Before
	public void beforeMethod() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@After
	public void afterMathod() throws Exception {
		this.executor.shutdown();
	}

	@AfterClass
	public static void shutdown() throws Exception {

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
		assertThat(entry.isStale()).isFalse();
		assertThat(entry.isEvicted()).isFalse();

		Thread.sleep(1010); //after soft ttl

		entry = cache.get(cacheKey);
		assertThat(entry.getValue()).isEqualTo(centry.getValue());
		assertThat(entry.isStale()).isTrue();
		assertThat(entry.isEvicted()).isFalse();

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

	/**
	 * synchronous - rethrows exceptions when missing or expired load happens 
	 * 
	 * @throws Exception
	 */
	@Test
	public void testDefaultSyncMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();

		TestSimpleLoader fetch = new TestSimpleLoader();
		ConfiguredCacheLoader<String> loader = new ConfiguredCacheLoader<String>(fetch);
		CacheLoadRequest<String> req = CacheLoadRequest.With(loader).cacheKey("Block").cache(2, 1, TimeUnit.SECONDS)
				.build();

		int rCounter = 0;
		fetch.setException(exception); //break updates
		try {
			cache.get(req);
			Assertions.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isEqualTo(exception);
		}
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setException(null); //allow updates

		CacheEntry<String> entry1 = cache.get(req); //fresh new entry
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(entry1.isStale()).isFalse();
		assertThat(entry1.isEvicted()).isFalse();
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setException(exception); //break updates

		Thread.sleep(500);//entry still fresh

		CacheEntry<String> entry2 = cache.get(req); //fresh from cache
		assertThat(entry2).isEqualTo(entry1);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue());
		assertThat(entry2.isStale()).isFalse();
		assertThat(entry2.isEvicted()).isFalse();
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired
		try {
			cache.get(req);
			Assertions.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isEqualTo(exception);
		}
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired
		try {
			cache.get(req);
			Assertions.fail("CacheLoaderException must be thrown by previous statement");
		} catch (CacheLoaderException clx) {
			//we want this
			assertThat(clx.getCause()).isEqualTo(exception);
		}
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setException(null); //allow updates

		CacheEntry<String> entry5 = cache.get(req); //fresh new value
		assertThat(entry5.getValue()).isNotNull();
		assertThat(entry5.isStale()).isFalse();
		assertThat(entry5.isEvicted()).isFalse();
		assertThat(entry5.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500); //entry still fresh

		CacheEntry<String> entry6 = cache.get(req);
		assertThat(entry6).isEqualTo(entry5); //fresh from cache
		assertThat(entry6.getValue()).isEqualTo(entry5.getValue());
		assertThat(entry6.isStale()).isFalse();
		assertThat(entry6.isEvicted()).isFalse();
		assertThat(entry6.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired

		CacheEntry<String> entry7 = cache.get(req);
		assertThat(entry7.getValue()).isNotEqualTo(entry6.getValue()); //new value is fetched
		assertThat(entry7.isStale()).isFalse();
		assertThat(entry7.isEvicted()).isFalse();
		assertThat(entry7.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		cache.close();
	}

	/**
	 * Sychronous that never throws expcetion but returns expired or null value entry
	 */
	@Test
	public void testSafeSyncMode() throws Exception {
		SpyMemcache<String> cache = buildMemcache();

		TestSimpleLoader fetch = new TestSimpleLoader();

		ConfiguredCacheLoader<String> loader = new ConfiguredCacheLoader<String>(fetch, MissingFailedRecipe.SYNC_NULL,
				ExpiredFailedRecipe.SYNC_RETURN);

		CacheLoadRequest<String> request = CacheLoadRequest.With(loader).cacheKey("Return").cache(2, 1, TimeUnit.SECONDS)
				.build();

		int rCounter = 0;
		fetch.setException(exception); // break cache entry updates

		CacheEntry<String> entry = cache.get(request);
		assertThat(entry).isNotNull(); //cache miss with exception - expired null value
		assertThat(entry.getValue()).isNull();
		assertThat(entry.isStale()).isTrue();
		assertThat(entry.isEvicted()).isTrue();
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setException(null); //allow cache entry updates

		CacheEntry<String> entry1 = cache.get(request); // fresh new value
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(entry1.isStale()).isFalse();
		assertThat(entry1.isEvicted()).isFalse();
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setException(exception); //break cache entry updates again

		Thread.sleep(500);//entry still fresh

		CacheEntry<String> entry2 = cache.get(request); //fresh from cache
		assertThat(entry2).isEqualTo(entry1);
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue());
		assertThat(entry2.isStale()).isFalse();
		assertThat(entry2.isEvicted()).isFalse();
		assertThat(rCounter).isEqualTo(fetch.getRequestCount()); //not new request
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastLoaded()); //loaded value

		Thread.sleep(1000); //entry soft expired

		CacheEntry<String> entry3 = cache.get(request);
		assertThat(entry3.getValue()).isEqualTo(entry1.getValue()); //get soft expired from cache and log error //XXX how to check error log?
		assertThat(entry3).isEqualTo(entry2);
		assertThat(entry3.getStaleTtl()).isEqualTo(entry2.getStaleTtl());
		assertThat(entry3.getEvictTtl()).isEqualTo(entry2.getEvictTtl());
		assertThat(entry3.isStale()).isTrue();
		assertThat(entry3.isEvicted()).isFalse();
		assertThat(entry3.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(2000); //entry hard expired

		CacheEntry<String> entry4 = cache.get(request);
		assertThat(entry4).isNotNull(); //cache miss with exception - expired null value
		assertThat(entry4.getValue()).isNull();
		assertThat(entry4.isStale()).isTrue();
		assertThat(entry4.isEvicted()).isTrue();
		assertThat(fetch.getRequestCount()).isEqualTo(++rCounter);

		fetch.setException(null); //allow cache entry updates again

		CacheEntry<String> entry5 = cache.get(request); //reloaded fresh entry
		assertThat(entry5.getValue()).isNotNull();
		assertThat(entry5.isStale()).isFalse();
		assertThat(entry5.isEvicted()).isFalse();
		assertThat(entry5.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(500); //entry still fresh

		CacheEntry<String> entry6 = cache.get(request); //refsh from cache
		assertThat(entry6.getValue()).isEqualTo(entry5.getValue());
		assertThat(entry6.isStale()).isFalse();
		assertThat(entry6.isEvicted()).isFalse();
		assertThat(entry6.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(1000); //entry soft expired - will be reloaded 
		CacheEntry<String> entry7 = cache.get(request);
		assertThat(entry7.isStale()).isFalse();
		assertThat(entry7.isEvicted()).isFalse();
		assertThat(entry7.getValue()).isNotEqualTo(entry5.getValue()); //not previous from cache
		assertThat(entry7.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		cache.close();
	}

	/**
	 * Default Async never throws Exceptions but returns null
	 */
	@Test
	public void testDefaultAsyncRefresh() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		Scheduler<String> scheduler = new Scheduler<String>(cache, executor);
		cache.setScheduler(scheduler);

		TestSimpleLoader fetch = new TestSimpleLoader();
		ConfiguredCacheLoader<String> loader = new ConfiguredCacheLoader<String>(fetch);
		CacheLoadRequest<String> req = CacheLoadRequest.With(loader).cacheKey("Async").cache(2, 1, TimeUnit.SECONDS)
				.async(true, true).build();

		int rCounter = 0;
		fetch.setException(exception); // break updates

		CacheEntry<String> entry0 = cache.get(req);
		assertThat(entry0).isEqualTo(CacheEntry.EMPTY);
		assertThat(entry0.getValue()).isNull(); //null is returned and async update is started
		assertThat(entry0.getCached()).isNull();
		assertThat(entry0.isStale()).isTrue();
		assertThat(entry0.isEvicted()).isTrue();

		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //request should be made

		fetch.setException(null); //allow updates

		entry0 = cache.get(req);
		assertThat(entry0).isEqualTo(CacheEntry.EMPTY); //still null
		assertThat(entry0.getValue()).isNull();
		assertThat(entry0.getCached()).isNull();
		assertThat(entry0.isStale()).isTrue();
		assertThat(entry0.isEvicted()).isTrue();
		Thread.sleep(50); //complete async refresh

		CacheEntry<String> entry1 = cache.get(req); //new cache value is asynchronously update
		assertThat(entry1.isStale()).isFalse();
		assertThat(entry1.isEvicted()).isFalse();
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setException(exception); //broke updates

		Thread.sleep(500);//entry still fresh

		CacheEntry<String> entry2 = cache.get(req); //fresh from cache
		assertThat(entry2).isEqualTo(entry1);
		assertThat(entry2.isStale()).isFalse();
		assertThat(entry2.isEvicted()).isFalse();
		assertThat(entry2.getValue()).isEqualTo(entry1.getValue());
		assertThat(entry2.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount()); // no new request

		Thread.sleep(1000); //entry soft expired

		CacheEntry<String> entry3 = cache.get(req); //soft expired cahce value & asynchronous update is started
		assertThat(entry3).isEqualTo(entry1);
		assertThat(entry3.getValue()).isEqualTo(entry1.getValue());
		assertThat(entry3.isStale());
		assertThat(entry3.isEvicted());

		Thread.sleep(50); //complete async update
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //failed update 

		Thread.sleep(2000); //entry hard expired

		assertThat(cache.get(req)).isEqualTo(CacheEntry.EMPTY); //async returns null & asynchronous update is started
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //failed update 

		fetch.setException(null); //allow updates

		assertThat(cache.get(req)).isEqualTo(CacheEntry.EMPTY); //async returns null & asynchronous update is started
		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //succesful update

		CacheEntry<String> entry4 = cache.get(req);
		assertThat(entry4.isStale()).isFalse();
		assertThat(entry4.isEvicted()).isFalse();
		assertThat(entry4.getValue()).isEqualTo(fetch.getLastLoaded()); // new value in cache
		assertThat(rCounter).isEqualTo(fetch.getRequestCount()); // no new request because of fresh cache hit

		Thread.sleep(1000); //entry soft expired

		CacheEntry<String> entry5 = cache.get(req); //soft expired cache value & asynchronous update is started
		assertThat(entry5.getValue()).isEqualTo(entry4.getValue());
		assertThat(entry5).isEqualTo(entry4);
		assertThat(entry5.isStale()).isTrue();
		assertThat(entry5.isEvicted()).isFalse();

		Thread.sleep(50); //complete async refresh
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //succesful update

		CacheEntry<String> entry6 = cache.get(req);//cache is refreshed
		assertThat(entry6.isStale()).isFalse();
		assertThat(entry6.isEvicted()).isFalse();
		assertThat(entry6.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(entry6.getValue()).isNotEqualTo(entry4.getValue());

		cache.close();
	}

	/**
	 * Cache expired entry on failed update
	 */
	@Test
	public void cacheExpiredFailedUpdate() throws Exception {

		SpyMemcache<String> cache = buildMemcache();
		Scheduler<String> scheduler = new Scheduler<String>(cache, executor);
		cache.setScheduler(scheduler);

		TestSimpleLoader fetch = new TestSimpleLoader();
		MissingFailedRecipe amis = new MissingFailedRecipe(LogErrorAs.MESSAGE, MissingReturn.NULL, CacheReturned.EXPIRED);
		ExpiredFailedRecipe aexp = new ExpiredFailedRecipe(LogErrorAs.MESSAGE, ExpiredReturn.EXPIRED, CacheReturned.EXPIRED);
		ConfiguredCacheLoader<String> loader = new ConfiguredCacheLoader<String>(fetch, amis, aexp);
		CacheLoadRequest<String> req = CacheLoadRequest.With(loader).cacheKey("Eternal").async(true, true)
				.cache(2, 1, TimeUnit.SECONDS).build();

		int rCounter = 0;

		CacheEntry<String> entry0 = cache.get(req);//cache miss & async update
		assertThat(entry0.getValue()).isNull();
		assertThat(entry0.getCached()).isNull();
		assertThat(entry0.isStale()).isTrue();
		assertThat(entry0.isEvicted()).isTrue();
		Thread.sleep(50); //complete async update
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry = cache.get(req);//fresh cache value
		assertThat(entry.isStale()).isFalse();
		assertThat(entry.isEvicted()).isFalse();
		assertThat(entry.getValue()).isEqualTo(fetch.getLastLoaded());

		fetch.setException(exception); //broke updates

		Thread.sleep(500);//entry still fresh

		CacheEntry<String> entry1 = cache.get(req); //fresh from cache
		assertThat(entry1).isEqualTo(entry);
		assertThat(entry1.isStale()).isFalse();
		assertThat(entry1.isEvicted()).isFalse();
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(rCounter).isEqualTo(fetch.getRequestCount()); //no new request

		Thread.sleep(1000); //entry soft expired

		CacheEntry<String> entry2 = cache.get(req); //expired from cache -> async update
		assertThat(entry2).isEqualTo(entry);
		assertThat(entry2.getValue()).isEqualTo(entry.getValue()); //value is same
		assertThat(entry2.isStale()).isTrue();
		assertThat(entry2.isEvicted()).isFalse();
		Thread.sleep(5); //complete async update failed -> ReturnOnExpired.EXPIRED as	CacheValue.EXPIRED
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry3 = cache.get(req); //expired (recached) from cache -> async update 
		assertThat(entry3.getValue()).isEqualTo(entry.getValue()); //value is same
		assertThat(entry3.isStale()).isTrue();
		assertThat(entry3.isEvicted()).isFalse();
		Thread.sleep(5); //complete async update (failed) - ReturnOnExpired.EXPIRED, CacheValue.EXPIRED
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		Thread.sleep(3005); //entry hard expired

		CacheEntry<String> entry4 = cache.get(req);
		assertThat(entry4).isEqualTo(CacheEntry.EMPTY); //cache miss & async update (failed) 
		Thread.sleep(50); //complete async update - failed - ReturnOnMissing.NULL, CacheValue.EXPIRED
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		fetch.setException(null); //enable updates

		CacheEntry<String> entry5 = cache.get(req); //cache miss -> async update 
		assertThat(entry5.getValue()).isNull();
		assertThat(entry5.isStale()).isTrue();
		assertThat(entry5.isEvicted()).isFalse();

		Thread.sleep(50); //complete async update (success)

		CacheEntry<String> entry7 = cache.get(req); //fresh from cache
		assertThat(entry7).isNotEqualTo(entry);
		assertThat(entry7.isStale()).isFalse();
		assertThat(entry7.isEvicted()).isFalse();
		assertThat(entry7.getValue()).isEqualTo(fetch.getLastLoaded());
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount()); //no new request

		cache.close();
	}

	/**
	 * Dodgy test because scheduler thread is doin cache checks in another thread every second 
	 */
	@Test
	public void testScheduled() throws Exception {
		SpyMemcache<String> cache = buildMemcache();
		Scheduler<String> scheduler = new Scheduler<String>(cache, executor);
		cache.setScheduler(scheduler);

		TestSimpleLoader fetch = new TestSimpleLoader();
		MissingFailedRecipe mis = new MissingFailedRecipe(LogErrorAs.MESSAGE, MissingReturn.NULL, CacheReturned.DONT);
		ExpiredFailedRecipe exp = new ExpiredFailedRecipe(LogErrorAs.MESSAGE, ExpiredReturn.EXPIRED, CacheReturned.DONT);
		ConfiguredCacheLoader<String> loader = new ConfiguredCacheLoader<String>(fetch, mis, exp);

		String key = "Scheduled";
		CacheLoadRequest<String> request = CacheLoadRequest.With(loader).async(false, false).cacheKey(key)
				.cache(2, 1, TimeUnit.SECONDS).build();

		int rCounter = 0;
		fetch.setException(exception); // break updates

		assertThat(cache.getScheduler().getScheduled().size()).isEqualTo(0);
		assertThat(cache.getScheduler().getScheduled(key)).isNull();

		cache.schedule(request);

		assertThat(cache.getScheduler().getScheduled().size()).isEqualTo(1);
		assertThat(cache.getScheduler().getScheduled(key).getUserKey()).isEqualTo(request.getUserKey());

		Thread.sleep(50); //scheduled refresh done (fail)
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		assertThat(cache.get(request.getUserKey())).isNull(); //cache miss

		Thread.sleep(1050);//scheduled refresh done (fail)

		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());
		assertThat(cache.get(request.getUserKey())).isNull(); //cache miss

		fetch.setException(null); // allow updates

		Thread.sleep(1050);//scheduled refresh done (pass)
		assertThat(++rCounter).isEqualTo(fetch.getRequestCount());

		CacheEntry<String> entry1 = cache.get(request);//cache is refreshed
		assertThat(entry1.isStale()).isFalse();
		assertThat(entry1.getValue()).isEqualTo(fetch.getLastLoaded());

		fetch.setException(exception); // break updates

		Thread.sleep(2050); //entry hard expired

		/*
		CachingSettings caching = new CachingSettings(key, 2, 1, TimeUnit.SECONDS);

		mis = new MissingErrorSettings(LogErrorAs.MESSAGE, MissingReturn.NULL, CacheReturned.EXPIRED);
		exp = new ExpiredErrorSettings(LogErrorAs.MESSAGE, ExpiredReturn.EXPIRED, CacheReturned.REQUEST);
		loader = new ConfiguredCacheLoader<String>(fetch, mis, exp);

		LoadingSettings<String> loading = new LoadingSettings<String>(loader, false, false);
		request = new CacheLoadRequest<String>(caching, loading);
		cache.schedule(request); //resubmit with new loader 

		assertThat(cache.getScheduler().getScheduled().size()).isEqualTo(1);
		assertThat(cache.getScheduler().getScheduled(key).getUserKey()).isEqualTo(request.getUserKey());

		assertThat(cache.getScheduler().getScheduled().size()).isEqualTo(1); //resubmitted but still only one
		*/
		cache.close();
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

	private static class TestSimpleLoader implements SimpleLoader<String> {

		private Logger logger = LoggerFactory.getLogger(getClass());

		private AtomicInteger rCounter = new AtomicInteger(0); //counts even failed exections

		private String lastLoaded = null;

		private Exception exception = null;

		private int latency = 0;

		@Override
		public String load() throws Exception {
			rCounter.incrementAndGet();
			if (latency > 0) {
				Thread.sleep(latency);
			}
			if (exception != null) {
				throw exception;
			}
			lastLoaded = "LOAD-" + new SimpleDateFormat("dd.MM.yyyy'T'HH:mm:ss.SSS").format(new Date());
			return lastLoaded;
		}

		public String getLastLoaded() {
			return lastLoaded;
		}

		public int getRequestCount() {
			return rCounter.get();
		}

		public int getLatency() {
			return latency;
		}

		public void setLatency(int latency) {
			this.latency = latency;
		}

		public void setException(Exception exception) {
			this.exception = exception;
		}

	};
}
