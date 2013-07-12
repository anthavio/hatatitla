package com.anthavio.httl.cache;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;

import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.anthavio.cache.CacheBase;
import com.anthavio.cache.CacheEntry;
import com.anthavio.cache.EHCache;
import com.anthavio.cache.HeapMapCache;
import com.anthavio.cache.SpyRequestCache;
import com.anthavio.httl.GetRequest;
import com.anthavio.httl.HttpClient4Sender;
import com.anthavio.httl.HttpSender;
import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.JokerServer;
import com.anthavio.httl.PostRequest;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.async.ExecutorServiceBuilder;
import com.anthavio.httl.cache.CachingRequest.RefreshMode;
import com.anthavio.httl.inout.ResponseBodyExtractor;
import com.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import com.anthavio.httl.inout.ResponseBodyExtractors;
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
public class CachingTest {

	private ThreadPoolExecutor executor;
	private CacheManager ehCacheManager;

	private MemCacheDaemon<LocalCacheElement> memcached;

	@BeforeClass
	public void setup() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.executor.shutdown();

		if (ehCacheManager != null) {
			ehCacheManager.shutdown();
		}
		if (memcached != null && memcached.isRunning()) {
			memcached.stop();
		}
	}

	private EHCache<CachedResponse> buildEhCache() {
		if (ehCacheManager == null) {
			ehCacheManager = CacheManager.create();
			Cache ehCache = new Cache("EHCache", 5000, false, false, 0, 0);
			ehCacheManager.addCache(ehCache);
		}
		EHCache<CachedResponse> cache = new EHCache<CachedResponse>("EHCache",
				ehCacheManager.getCache("EHCache"));
		return cache;
	}

	private SpyRequestCache<CachedResponse> buildMemcache() throws IOException {
		if (memcached == null) {
			memcached = new MemCacheDaemon<LocalCacheElement>();

			int maxItems = 5000;
			long maxBytes = 10 * 1024 * 1024;
			CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(
					ConcurrentLinkedHashMap.EvictionPolicy.FIFO, maxItems, maxBytes);
			memcached.setCache(new CacheImpl(storage));
			memcached.setBinary(false);
			memcached.setAddr(new InetSocketAddress(11311));
			memcached.setIdleTime(1000);
			memcached.setVerbose(true);
			memcached.start();
		}

		MemcachedClient client = new MemcachedClient(AddrUtil.getAddresses("localhost:11311"));
		SpyRequestCache<CachedResponse> cache = new SpyRequestCache<CachedResponse>("whatever", client, 1, TimeUnit.SECONDS);
		return cache;
	}

	private CachingSender newCachedSender(int port) {
		String url = "http://localhost:" + port;
		//HttpSender sender = new SimpleHttpSender(url);
		HttpSender sender = new HttpClient4Sender(url);
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		csender.setExecutor(executor);
		return csender;
	}

	@Test
	public void testSameRequestDifferentSender() throws IOException {
		JokerServer server = new JokerServer().start();
		HttpSender sender1 = new HttpClient4Sender("127.0.0.1:" + server.getHttpPort());
		HttpSender sender2 = new HttpClient4Sender("localhost:" + server.getHttpPort());
		//shared cache for 2 senders
		CacheBase<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender1 = new CachingSender(sender1, cache);
		CachingSender csender2 = new CachingSender(sender2, cache);
		SenderRequest request1 = new GetRequest("/").addParameter("docache", 1);
		SenderRequest request2 = new GetRequest("/").addParameter("docache", 1);

		SenderResponse response1 = csender1.execute(request1, 1, TimeUnit.SECONDS);
		SenderResponse response2 = csender2.execute(request2, 1, TimeUnit.SECONDS);
		assertThat(response2).isNotEqualTo(response1); //different sender - different host!

		//switch Request execution to different Sender
		SenderResponse response3 = csender1.execute(request2, 1, TimeUnit.SECONDS);
		assertThat(response3).isEqualTo(response1); //same sender
		assertThat(response3).isNotEqualTo(response2); //different sender - different host!

		SenderResponse response4 = csender2.execute(request1, 1, TimeUnit.SECONDS);
		assertThat(response4).isNotEqualTo(response1); //different sender - different host!
		assertThat(response4).isEqualTo(response2); //same sender

		sender1.close();
		sender2.close();
		server.stop();
	}

	@Test
	public void testAutomaticRefresh() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingSender csender = newCachedSender(server.getHttpPort());
		SenderRequest request = new GetRequest("/cs").addParameter("sleep", 1);
		ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		CachingRequest crequest = new CachingRequest(request, 4, 2, TimeUnit.SECONDS, RefreshMode.SCHEDULED); //automatic updates!

		final int initialCount = server.getRequestCount();
		SenderResponse response1 = csender.execute(crequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);
		response1.close();

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //background refresh

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //background refresh

		long m1 = System.currentTimeMillis();
		SenderResponse response2 = csender.execute(crequest);
		assertThat(System.currentTimeMillis() - m1).isLessThan(10);//from cache - must be quick!
		assertThat(response2).isNotEqualTo(response1); //different
		response2.close();

		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
		Thread.sleep(5010); //after hard expiry + server sleep

		SenderResponse response = csender.execute(crequest);
		assertThat(response.getHttpStatusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR);
		assertThat(response).isNotInstanceOf(CachedResponse.class); //errors are not cached
		response.close();

		csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete

		server.stop();
	}

	@Test
	public void testSimpleCache() throws Exception {
		CacheBase<CachedResponse> cache;
		cache = new HeapMapCache<CachedResponse>();
		doCacheTest(cache);
	}

	@Test
	public void testEhCache() throws Exception {
		CacheBase<CachedResponse> cache;
		cache = buildEhCache();
		//cache = buildMemcache();
		doCacheTest(cache);
	}

	@Test
	public void testMemcached() throws Exception {
		CacheBase<CachedResponse> cache;
		cache = buildMemcache();
		doCacheTest(cache);
	}

	@Test
	public void testHttpCacheControlCaching() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingSender csender = newCachedSender(server.getHttpPort());
		//http headers will allow to cache reponse for 1 second
		SenderRequest request = new GetRequest("/").addParameter("docache", 1);
		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		//System.out.println("Doing initial request");

		ExtractedBodyResponse<String> extract1 = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached

		//System.out.println("Going to the cache");

		ExtractedBodyResponse<String> extract2 = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1); //count is same as before
		assertThat(extract2.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getBody()).isEqualTo(extract1.getBody()); //1,2 extracted are equal

		Thread.sleep(1300); //let the cache entry expire

		ExtractedBodyResponse<String> extract3 = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract3.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract3.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract3.getResponse()).isNotSameAs(extract1.getResponse()); //not equal anymore!

		ExtractedBodyResponse<String> extract4 = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract4.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract4.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract4.getResponse()).isSameAs(extract3.getResponse()); //3,4 are same object!
		assertThat(extract4.getBody()).isEqualTo(extract3.getBody()); //3,4 extracted are equal
		assertThat(extract4.getBody()).isEqualTo(extract3.getBody()); //1,4 extracted are equal

		csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	@Test
	public void testHttpETagCaching() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingSender csender = newCachedSender(server.getHttpPort());

		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		//http headers will use ETag
		SenderRequest request1 = new GetRequest("/").addParameter("doetag");
		ExtractedBodyResponse<String> extract1 = csender.extract(request1, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract1.getExtracted());
		//System.out.println(extract1.getResponse());

		Thread.sleep(100); //

		SenderRequest request2 = new GetRequest("/").addParameter("doetag");
		ExtractedBodyResponse<String> extract2 = csender.extract(request2, ResponseBodyExtractors.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2 (304 NOT_MODIFIED)
		assertThat(extract2.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract2.getExtracted());
		//System.out.println(extract2.getResponse());
		//assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getBody()).isEqualTo(extract1.getBody()); //1,2 extracted are equal

		csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete
		server.stop();
	}

	/**
	 * Basic caching operations we expect to work with any cache implementation
	 */
	private void doCacheTest(CacheBase<CachedResponse> cache) throws InterruptedException, IOException {

		CachedResponse cresponse = new CachedResponse(200, "Choroso", new Multival(), new Date().toString());
		String cacheKey = String.valueOf(System.currentTimeMillis());
		//hard ttl is 2 seconds
		Boolean added = cache.set(cacheKey, new CacheEntry<CachedResponse>(cresponse, 2, 1));
		assertThat(added).isTrue();

		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		assertThat(entry.getValue().getAsString()).isEqualTo(cresponse.getAsString());
		assertThat(entry.isSoftExpired()).isFalse();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(1010); //after soft ttl

		entry = cache.get(cacheKey);
		assertThat(entry.getValue().getAsString()).isEqualTo(cresponse.getAsString());
		assertThat(entry.isSoftExpired()).isTrue();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(2010); //after hard ttl + 1 second (memcached has this whole second precision)

		entry = cache.get(cacheKey);
		assertThat(entry).isNull();
	}

	//@Test
	public void testFacebook() throws IOException {
		//https://developers.facebook.com/tools/explorer
		//Facebook uses ETag
		//ETag: "9ea8f5a5b1d659bc8358daad6f2e347f15f6e683"
		//Expires: Sat, 01 Jan 2000 00:00:00 GMT
		HttpSender sender = new HttpClient4Sender("https://graph.facebook.com");
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		GetRequest request = new GetRequest("/me/friends");
		request
				.addParameter(
						"access_token",
						"AAAAAAITEghMBAOK0zAh6obLGcPm5FuXt3OlqMWPmgudEF9KxrVBiNt6AjccUFCSoZAxRr8ZBvGZBi9sOdZBxebQZBJzVMwRKzIWCLZCjzxGV0cvAMkDjXu");
		//request.addParameter("fields", "id,name");
		ExtractedBodyResponse<String> response = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(response.getResponse()).isInstanceOf(CachedResponse.class);

		ExtractedBodyResponse<String> response2 = csender.extract(request, ResponseBodyExtractors.STRING);
		assertThat(response2.getResponse()).isInstanceOf(CachedResponse.class);
		assertThat(response2.getResponse()).isEqualTo(response.getResponse());
		System.out.println(response.getBody());
	}

	//https://code.google.com/apis/console
	//@Test
	public void testGoogleApi() throws Exception {

		//https://developers.google.com/maps/documentation/staticmaps/
		//Google maps uses Expires
		//Cache-Control: public, max-age=86400
		//Date: Tue, 26 Feb 2013 16:11:16 GMT
		//Expires: Wed, 27 Feb 2013 16:11:16 GMT
		HttpSender sender = new HttpClient4Sender("http://maps.googleapis.com/maps/api/staticmap");
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		GetRequest request = new GetRequest("/");
		request.addParameter("key", "AIzaSyCgNUVqbYTyIP_f4Ew2wJXSZ9XjIQ8F5w8");
		request.addParameter("center", "51.477222,0");
		request.addParameter("size", "10x10");
		request.addParameter("sensor", false);
		ExtractedBodyResponse<String> extract = sender.extract(request, String.class);
		System.out.println(extract.getBody());
		sender.close();

		if (true)
			return;

		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(new FileInputStream("/Users/martin.vanek/Downloads/google_privatekey.p12"),
				"notasecret".toCharArray());
		PrivateKey privateKey = (PrivateKey) keyStore.getKey("privatekey", "notasecret".toCharArray());
		String header = Base64.encodeBase64String("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
		System.out.println(header);
		long iat = System.currentTimeMillis() / 1000;
		long exp = iat + 60 * 60;
		String data = "{" //
				+ "\"iss\":\"164620382615@developer.gserviceaccount.com\","//
				+ "\"scope\":\"https://www.googleapis.com/auth/prediction\","//
				+ "\"aud\":\"https://accounts.google.com/o/oauth2/token\"," //
				+ "\"exp\":" + exp + "," + "\"iat\":" + iat//
				+ "}";
		String claimset = Base64.encodeBase64String(data.getBytes());
		String content = header + "." + claimset;

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(content.getBytes());
		byte[] sign = signature.sign();
		String digsig = Base64.encodeBase64String(sign);
		String assertion = content + "." + digsig;
		System.out.println(assertion);
		sender = new HttpClient4Sender("https://accounts.google.com/o/oauth2/token");
		PostRequest request2 = sender.POST("").build();
		request2.addParameter("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		request2.addParameter("assertion", assertion);
		ExtractedBodyResponse<String> x = sender.extract(request2, String.class);
		System.out.println(x.getBody());
	}

	//@Test
	public void testGoogle() throws IOException {
		//google protect itself from being cached
		//Expires: -1
		//Cache-Control: private, max-age=0
		HttpSender sender = new HttpClient4Sender("http://www.google.co.uk");
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		SenderResponse response = csender.execute(new GetRequest("/"));
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response).isNotInstanceOf(CachedResponse.class); //not cached!
		response.close();

		//enforce static caching
		SenderResponse response2 = csender.execute(new GetRequest("/"), 5, TimeUnit.MINUTES);
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response2).isInstanceOf(CachedResponse.class);
		response2.close();

		SenderResponse response3 = csender.execute(new GetRequest("/"), 5, TimeUnit.MINUTES);
		assertThat(response3).isEqualTo(response2); //must be same instance 
		response3.close();
	}
}
