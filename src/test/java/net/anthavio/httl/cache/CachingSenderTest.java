package net.anthavio.httl.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.cache.CacheLoadRequest;
import net.anthavio.cache.Scheduler;
import net.anthavio.cache.impl.HeapMapCache;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestBuilders.SenderBodyRequestBuilder;
import net.anthavio.httl.HttlRequestBuilders.SenderNobodyRequestBuilder;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.JokerServer;
import net.anthavio.httl.async.ExecutorServiceBuilder;
import net.anthavio.httl.marshall.HttlStringExtractor;
import net.anthavio.httl.transport.HttpClient4Config;
import net.anthavio.httl.util.Base64;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingSenderTest {

	private JokerServer server;
	private ThreadPoolExecutor executor;

	@Before
	public void setup() throws Exception {
		this.server = new JokerServer().start();
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();
	}

	@After
	public void shutdown() throws Exception {
		this.executor.shutdown();
		this.server.stop();
	}

	private CachingSender newCachingSender(int port) {
		String url = "http://localhost:" + port;
		//HttpSender sender = new SimpleHttpSender(url);
		HttlSender sender = new HttpClient4Config(url).sender().build();
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		Scheduler<CachedResponse> scheduler = new Scheduler<CachedResponse>(cache, executor);
		cache.setScheduler(scheduler);
		return csender;
	}

	@Test
	public void testSameRequestDifferentSender() throws IOException {

		HttlSender sender1 = new HttpClient4Config("127.0.0.1:" + server.getPortHttp()).sender().build();//different host name
		HttlSender sender2 = new HttpClient4Config("localhost:" + server.getPortHttp()).sender().build();//different host name
		//shared cache for 2 senders
		CacheBase<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender1 = new CachingSender(sender1, cache);
		CachingSender csender2 = new CachingSender(sender2, cache);
		HttlRequest request1 = sender1.GET("/").param("docache", 1).build();
		HttlRequest request2 = sender2.GET("/").param("docache", 1).build();

		HttlResponse response1 = csender1.from(request1).evictTtl(1, TimeUnit.SECONDS).execute();
		HttlResponse response2 = csender2.from(request2).evictTtl(1, TimeUnit.SECONDS).execute();
		assertThat(response2).isNotEqualTo(response1); //different sender - different host!

		//switch Request execution to different Sender
		HttlResponse response3 = csender1.from(request2).evictTtl(1, TimeUnit.SECONDS).execute();
		assertThat(response3).isEqualTo(response1); //same sender
		assertThat(response3).isNotEqualTo(response2); //different sender - different host!

		HttlResponse response4 = csender2.from(request1).evictTtl(1, TimeUnit.SECONDS).execute();
		assertThat(response4).isNotEqualTo(response1); //different sender - different host!
		assertThat(response4).isEqualTo(response2); //same sender

		sender1.close();
		sender2.close();
	}

	//FIXME @Test works differently now
	public void testAutomaticRefresh() throws Exception {
		CachingSender csender = newCachingSender(server.getPortHttp());
		HttlRequest request = csender.getSender().GET("/cs").param("sleep", 0).build();
		//ResponseBodyExtractor<String> extractor = ResponseBodyExtractors.STRING;
		CachingSenderRequest crequest = csender.from(request).cache(4, 2, TimeUnit.SECONDS).build();

		CacheLoadRequest<CachedResponse> loadRequest = csender.convert(crequest);
		csender.getCache().schedule(loadRequest);
		final int initialCount = server.getRequestCount();
		assertThat(csender.execute(crequest)).isNull();

		Thread.sleep(2000); //server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);
		CacheEntry<CachedResponse> response1 = csender.execute(crequest);
		assertThat(response1.isStale() == false);
		response1.getValue().close();

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //background refresh

		Thread.sleep(2000 + 1010); //after soft expiry + server sleep
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 3); //background refresh

		long m1 = System.currentTimeMillis();
		CacheEntry<CachedResponse> response2 = csender.execute(crequest);
		assertThat(System.currentTimeMillis() - m1).isLessThan(10);//from cache - must be quick!
		assertThat(response2.isStale() == false);
		assertThat(response2).isNotEqualTo(response1); //different
		response2.getValue().close();

		Thread.sleep(1500); //cache some value
		CacheEntry<CachedResponse> response3 = csender.execute(crequest);
		assertThat(response3.isStale() == false);

		server.setHttpCode(HttpURLConnection.HTTP_INTERNAL_ERROR); //disable refreshes

		Thread.sleep(2500); //soft expiry 
		CacheEntry<CachedResponse> response4 = csender.execute(crequest);
		assertThat(response4.isStale()); //soft expired
		assertThat(response4).isEqualTo(response3);

		Thread.sleep(2100); //after hard expiry
		assertThat(csender.execute(crequest)).isNull(); //nothing since hard expired
		//CacheEntry<CachedResponse> response = csender.execute(crequest);
		//assertThat(response.getValue().getHttpStatusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR);
		//assertThat(response).isNotInstanceOf(CachedResponse.class); //errors are not cached
		//response.getValue().close();

		//csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	@Test
	public void testHttpCacheControlCaching() throws Exception {
		CachingSender csender = newCachingSender(server.getPortHttp());
		//http headers will allow to cache reponse for 1 second
		HttlRequest request = csender.getSender().GET("/").param("docache", 1).build();
		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		//System.out.println("Doing initial request");

		ExtractedResponse<String> extract1 = csender.extract(request, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached

		//System.out.println("Going to the cache");

		ExtractedResponse<String> extract2 = csender.extract(request, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1); //count is same as before
		assertThat(extract2.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getBody()).isEqualTo(extract1.getBody()); //1,2 extracted are equal

		Thread.sleep(1300); //let the cache entry expire

		ExtractedResponse<String> extract3 = csender.extract(request, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract3.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract3.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract3.getResponse()).isNotSameAs(extract1.getResponse()); //not equal anymore!

		ExtractedResponse<String> extract4 = csender.extract(request, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract4.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract4.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract4.getResponse()).isSameAs(extract3.getResponse()); //3,4 are same object!
		assertThat(extract4.getBody()).isEqualTo(extract3.getBody()); //3,4 extracted are equal
		assertThat(extract4.getBody()).isEqualTo(extract3.getBody()); //1,4 extracted are equal

		//csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	//@Test TODO revisit and fix
	public void testHttpETagCaching() throws Exception {
		CachingSender csender = newCachingSender(server.getPortHttp());

		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		//http headers will use ETag
		HttlRequest request1 = csender.getSender().GET("/").param("doetag").build();
		ExtractedResponse<String> extract1 = csender.extract(request1, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract1.getExtracted());
		//System.out.println(extract1.getResponse());

		Thread.sleep(100); //

		HttlRequest request2 = csender.getSender().GET("/").param("doetag").build();
		ExtractedResponse<String> extract2 = csender.extract(request2, HttlStringExtractor.STANDARD);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2 (304 NOT_MODIFIED)
		assertThat(extract2.getResponse().getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract2.getExtracted());
		//System.out.println(extract2.getResponse());
		//assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getBody()).isEqualTo(extract1.getBody()); //1,2 extracted are equal

		//csender.close();
		Thread.sleep(1010); //let the potential server sleep request complete
	}

	//@Test
	public void testFacebook() throws IOException {
		//https://developers.facebook.com/tools/explorer
		//Facebook uses ETag
		//ETag: "9ea8f5a5b1d659bc8358daad6f2e347f15f6e683"
		//Expires: Sat, 01 Jan 2000 00:00:00 GMT
		HttlSender sender = new HttpClient4Config("https://graph.facebook.com").sender().build();
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		HttlRequest request = sender
				.GET("/me/friends")
				.param(
						"access_token",
						"AAAAAAITEghMBAOK0zAh6obLGcPm5FuXt3OlqMWPmgudEF9KxrVBiNt6AjccUFCSoZAxRr8ZBvGZBi9sOdZBxebQZBJzVMwRKzIWCLZCjzxGV0cvAMkDjXu")
				.build();
		//request.addParameter("fields", "id,name");
		ExtractedResponse<String> response = csender.extract(request, HttlStringExtractor.STANDARD);
		assertThat(response.getResponse()).isInstanceOf(CachedResponse.class);

		ExtractedResponse<String> response2 = csender.extract(request, HttlStringExtractor.STANDARD);
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
		HttlSender sender = new HttpClient4Config("http://maps.googleapis.com/maps/api/staticmap").sender().build();
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		SenderNobodyRequestBuilder request = sender.GET("/");
		request.param("key", "AIzaSyCgNUVqbYTyIP_f4Ew2wJXSZ9XjIQ8F5w8");
		request.param("center", "51.477222,0");
		request.param("size", "10x10");
		request.param("sensor", false);
		ExtractedResponse<String> extract = sender.extract(request.build(), String.class);
		System.out.println(extract.getBody());
		sender.close();

		if (true)
			return;

		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(new FileInputStream("/Users/martin.vanek/Downloads/google_privatekey.p12"),
				"notasecret".toCharArray());
		PrivateKey privateKey = (PrivateKey) keyStore.getKey("privatekey", "notasecret".toCharArray());
		String header = Base64.encodeString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
		System.out.println(header);
		long iat = System.currentTimeMillis() / 1000;
		long exp = iat + 60 * 60;
		String data = "{" //
				+ "\"iss\":\"164620382615@developer.gserviceaccount.com\","//
				+ "\"scope\":\"https://www.googleapis.com/auth/prediction\","//
				+ "\"aud\":\"https://accounts.google.com/o/oauth2/token\"," //
				+ "\"exp\":" + exp + "," + "\"iat\":" + iat//
				+ "}";
		String claimset = Base64.encodeString(data);
		String content = header + "." + claimset;

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(content.getBytes());
		byte[] sign = signature.sign();
		String digsig = new String(Base64.encode(sign));
		String assertion = content + "." + digsig;
		System.out.println(assertion);
		sender = new HttpClient4Config("https://accounts.google.com/o/oauth2/token").sender().build();
		SenderBodyRequestBuilder request2 = sender.POST("");
		request2.param("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		request2.param("assertion", assertion);
		ExtractedResponse<String> x = sender.extract(request2.build(), String.class);
		System.out.println(x.getBody());
	}

	//@Test
	public void testGoogle() throws IOException {
		//google protect itself from being cached
		//Expires: -1
		//Cache-Control: private, max-age=0
		HttlSender sender = new HttpClient4Config("http://www.google.co.uk").sender().build();
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		HttlResponse response = csender.execute(sender.GET("/").build());
		assertThat(response.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response).isNotInstanceOf(CachedResponse.class); //not cached!
		response.close();

		//enforce static caching
		HttlResponse response2 = csender.from(sender.GET("/").build()).evictTtl(5, TimeUnit.MINUTES).execute();
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response2).isInstanceOf(CachedResponse.class);
		response2.close();

		HttlResponse response3 = csender.from(sender.GET("/").build()).evictTtl(5, TimeUnit.MINUTES).execute();
		assertThat(response3).isEqualTo(response2); //must be same instance 
		response3.close();
	}
}
