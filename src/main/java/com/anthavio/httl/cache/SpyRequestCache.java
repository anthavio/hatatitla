package com.anthavio.httl.cache;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

/**
 * SpyMemcached implementation
 * 
 * @author martin.vanek
 *
 */
public class SpyRequestCache<V extends Serializable> extends RequestCache<V> {

	public static final int MINUTE = 60; //seconds
	public static final int HOUR = 60 * MINUTE;//seconds
	public static final int DAY = 24 * HOUR;//seconds
	public static final int MONTH = 30 * DAY; //maximum value as interval. Greater value is considered as epoch time (seconds after 1.1.1970T00:00:00)

	private static final int MaxKeyLength = 250;

	private final MemcachedClient client;

	private final long timeout; //memcached timeout in milliseconds

	public SpyRequestCache(String name, MemcachedClient client, ConnectionFactory connectionFactory,
			List<InetSocketAddress> addrs) throws IOException {
		super(name);
		this.client = new MemcachedClient(connectionFactory, addrs);
		this.timeout = connectionFactory.getOperationTimeout(); //millis is what it is
	}

	public SpyRequestCache(String name, MemcachedClient client, long timeout, TimeUnit unit) {
		super(name);
		this.client = client;
		this.timeout = unit.toMillis(timeout);
	}

	@Override
	public CacheEntry<V> doGet(String key) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum");
		}
		GetFuture<Object> future = client.asyncGet(key);
		return (CacheEntry<V>) future.get(timeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public Boolean doSet(String key, CacheEntry<V> entry) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum");
		}
		int ttlMillis = (int) entry.getHardTtl();
		OperationFuture<Boolean> future = client.set(key, ttlMillis, entry);
		return future.get(timeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public Boolean doRemove(String key) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum");
		}
		OperationFuture<Boolean> future = client.delete(key);
		return future.get(timeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public void removeAll() {
		//namespace versioning!
		String nsVersionKey = "namespace:" + getName(); //externalize 
		if (client.incr(nsVersionKey, 1) == -1) {
			try {
				OperationFuture<Boolean> future = client.set(nsVersionKey, DAY, nsVersionKey);
				future.get(timeout, TimeUnit.MILLISECONDS);
			} catch (Exception x) {
				logger.warn("Failed to removeAll", x);
			}
		}
	}

	public String getNamespace() throws Exception {
		String nsVersionKey = "namespace:" + getName(); //externalize 
		GetFuture<Object> future = client.asyncGet(nsVersionKey);
		Integer nsVersion = (Integer) future.get(timeout, TimeUnit.MILLISECONDS);
		int icnt = 0;
		while (nsVersion == null && ++icnt < 5) {
			boolean added = doAdd(nsVersionKey, DAY, 1);
			if (!added) { //somebody else was faster...
				future = client.asyncGet(nsVersionKey);
				nsVersion = (Integer) future.get(timeout, TimeUnit.MILLISECONDS);
			}
		}
		if (nsVersion == null) {
			throw new IllegalStateException("Failed to get namespace version in " + icnt + " attempts");
		}
		return getName() + "-" + nsVersion;
	}

	@Override
	public void destroy() {
		client.shutdown();
	}

	private boolean doAdd(String key, int exp, Serializable value) throws Exception {
		OperationFuture<Boolean> future = client.add(key, exp, value);
		return future.get(timeout, TimeUnit.MILLISECONDS);
	}

	//default Transcoder is SerializingTranscoder
	/*
	private static class CacheEntryTranscoder implements Transcoder<CacheEntry<CachedResponse>> {

		@Override
		public boolean asyncDecode(CachedData d) {
			return false;
		}

		@Override
		public CachedData encode(CacheEntry<CachedResponse> o) {
			return null;
		}

		@Override
		public CacheEntry<CachedResponse> decode(CachedData d) {
			return null;
		}

		@Override
		public int getMaxSize() {
			return 0;
		}
	}
	*/
}
