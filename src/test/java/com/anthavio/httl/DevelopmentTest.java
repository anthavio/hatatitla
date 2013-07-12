package com.anthavio.httl;

import java.util.concurrent.ThreadPoolExecutor;

import com.anthavio.cache.HeapMapCache;
import com.anthavio.httl.async.ExecutorServiceBuilder;
import com.anthavio.httl.cache.CachedResponse;
import com.anthavio.httl.cache.CachingExtractor;
import com.anthavio.httl.cache.CachingSender;

/**
 * 
 * @author martin.vanek
 *
 */
public class DevelopmentTest {

	public static void main(String[] args) {
		MarshallingExtractingTest test = new MarshallingExtractingTest();
		try {
			test.setup();
			test.devel();
		} catch (Exception x) {
			x.printStackTrace();
		} finally {
			try {
				test.destroy();
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	private static CachingExtractor newExtractorSender(int port) {
		String url = "http://localhost:" + port;
		HttpSender sender = new HttpURLSender(url);
		//HttpSender sender = new HttpClient4Sender(url);
		HeapMapCache<Object> cache = new HeapMapCache<Object>();
		CachingExtractor csender = new CachingExtractor(sender, cache);

		ThreadPoolExecutor executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0)
				.setMaximumPoolSize(1).setMaximumQueueSize(0).build();
		csender.setExecutor(executor);
		return csender;
	}

	private static CachingSender newCachedSender(int port) {
		String url = "http://localhost:" + port;
		//HttpSender sender = new SimpleHttpSender(url);
		HttpSender sender = new HttpClient4Sender(url);
		HeapMapCache<CachedResponse> cache = new HeapMapCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		ThreadPoolExecutor executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0)
				.setMaximumPoolSize(1).setMaximumQueueSize(0).build();
		csender.setExecutor(executor);
		return csender;
	}
}
