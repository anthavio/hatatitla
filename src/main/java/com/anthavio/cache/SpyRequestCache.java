package com.anthavio.cache;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

import com.anthavio.httl.util.Cutils;

/**
 * SpyMemcached implementation
 * 
 * @author martin.vanek
 *
 */
public class SpyRequestCache<V extends Serializable> extends CacheBase<V> {

	public static final int MINUTE = 60; //seconds
	public static final int HOUR = 60 * MINUTE;//seconds
	public static final int DAY = 24 * HOUR;//seconds
	public static final int MONTH = 30 * DAY; //Memcached maximum value as interval. Greater value is considered as epoch time (seconds after 1.1.1970T00:00:00)

	private static final int MaxKeyLength = 250; //Memcached limit for key

	private static final String VERSION_IN = "1"; //Must be String "1" - number cannot be incremented - Memcached is ASSHOLE

	private final MemcachedClient client;

	private final boolean clientCreated;

	private final boolean namespaceVersioning;

	private final long operationTimeout; //Memcached call timeout in milliseconds

	public SpyRequestCache(String name, ConnectionFactory connectionFactory, List<InetSocketAddress> addrs,
			boolean namespaceVersioning) throws IOException {
		super(name);
		if (Cutils.isBlank(name)) {
			throw new IllegalArgumentException("SpyRequestCache name must not be blank");
		}
		this.client = new MemcachedClient(connectionFactory, addrs);
		this.clientCreated = true;
		this.operationTimeout = connectionFactory.getOperationTimeout();
		this.namespaceVersioning = namespaceVersioning;
	}

	public SpyRequestCache(String name, MemcachedClient client, long operationTimeout, TimeUnit unitOfTimeout) {
		this(name, client, operationTimeout, unitOfTimeout, false);
	}

	public SpyRequestCache(String name, MemcachedClient client, long operationTimeout, TimeUnit unitOfTimeout,
			boolean namespaceVersioning) {
		super(name);
		if (Cutils.isBlank(name)) {
			throw new IllegalArgumentException("SpyRequestCache name must not be blank");
		}
		this.client = client;
		this.clientCreated = false;
		this.operationTimeout = unitOfTimeout.toMillis(operationTimeout);
		this.namespaceVersioning = namespaceVersioning;
	}

	/**
	 * @return Spy Memcached client
	 */
	public MemcachedClient getClient() {
		return client;
	}

	/**
	 * @return Memcached timeout for operation
	 */
	public long getOperationTimeout() {
		return operationTimeout;
	}

	@Override
	public CacheEntry<V> doGet(String key) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		GetFuture<Object> future = client.asyncGet(key);
		return (CacheEntry<V>) future.get(operationTimeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public Boolean doSet(String key, CacheEntry<V> entry) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		int ttlMillis = (int) entry.getHardTtl();
		OperationFuture<Boolean> future = client.set(key, ttlMillis, entry);
		return future.get(operationTimeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public Boolean doRemove(String key) throws Exception {
		if (key.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		OperationFuture<Boolean> future = client.delete(key);
		return future.get(operationTimeout, TimeUnit.MILLISECONDS);
	}

	@Override
	public String getKey(String userKey) {
		if (namespaceVersioning) {
			return getNamespace() + ":" + userKey;
		} else {
			return getName() + ":" + userKey;
		}
	}

	/**
	 * Changing namespace by incrementing it's version results in (virtally) removing all entries means  
	 * because namespace is part of key - all keys became invalid
	 */
	@Override
	public void removeAll() {
		if (namespaceVersioning) {
			String nsVersionKey = getNsVersionKey();
			long incr = client.incr(nsVersionKey, 1);
			if (incr == -1) {//does not not exist -> create it
				try {
					OperationFuture<Boolean> future = client.set(nsVersionKey, DAY, VERSION_IN);
					future.get(operationTimeout, TimeUnit.MILLISECONDS);
				} catch (Exception x) {
					logger.warn("Failed to removeAll", x);
				}
			}
			logger.debug("Namespace version set to " + getName() + ":" + incr);
		} else {
			throw new IllegalStateException("Cannot remove all. Namespace versioning is not enabled.");
		}
	}

	@Override
	public void close() {
		//if client instance was given to us (in constructor), we are NOT in charge of shutting it down
		if (clientCreated) {
			client.shutdown();
		}
	}

	/**
	 * @return Key for namespace version of this cache
	 */
	private String getNsVersionKey() {
		return "namespace-version:" + getName();
	}

	/**
	 * Namespace is used as prefix for every key to avoid clashes
	 */
	public String getNamespace() {
		if (namespaceVersioning) {
			return getName() + ":" + getNsVersion();
		} else {
			return getName();
		}
	}

	private String getNsVersion() {
		String nsVersionKey = getNsVersionKey();
		try {
			GetFuture<Object> gfuture = client.asyncGet(nsVersionKey);
			String nsVersion = (String) gfuture.get(operationTimeout, TimeUnit.MILLISECONDS);
			int icnt = 0;
			while (nsVersion == null && ++icnt < 5) {
				OperationFuture<Boolean> afuture = client.add(nsVersionKey, DAY, VERSION_IN);
				boolean added = afuture.get(operationTimeout, TimeUnit.MILLISECONDS);
				if (!added) { //somebody else was faster...so get what's there
					gfuture = client.asyncGet(nsVersionKey);
					nsVersion = (String) gfuture.get(operationTimeout, TimeUnit.MILLISECONDS);
				}
			}

			if (nsVersion == null) {
				throw new IllegalStateException("Failed to obtain namespace version for " + nsVersionKey + " in " + icnt
						+ " attempts");
			}
			return nsVersion;
		} catch (Exception x) {
			throw new IllegalStateException("Failed to obtain namespace version for " + nsVersionKey, x);
		}
	}

	private boolean doAdd(String key, int expiry, Serializable value) throws InterruptedException, TimeoutException,
			ExecutionException {
		OperationFuture<Boolean> future = client.add(key, expiry, value);
		return future.get(operationTimeout, TimeUnit.MILLISECONDS);
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
