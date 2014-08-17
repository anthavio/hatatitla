package net.anthavio.cache.impl;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CacheBase;
import net.anthavio.cache.CacheEntry;
import net.anthavio.httl.util.Cutils;
import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.CheckedOperationTimeoutException;

/**
 * SpyMemcached implementation
 * 
 * TODO constructor is too lenghty - introduce Builder
 * 
 * @author martin.vanek
 *
 */
public class SpyMemcache<V extends Serializable> extends CacheBase<V> {

	public static final int MINUTE = 60; //seconds
	public static final int HOUR = 60 * MINUTE;//seconds
	public static final int DAY = 24 * HOUR;//seconds
	public static final int MONTH = 30 * DAY; //Memcached maximum value as interval. Greater value is considered as epoch time (seconds after 1.1.1970T00:00:00)

	private static final int MaxKeyLength = 250; //Memcached limit for key

	private static final String VERSION_IN = "1"; //Must be String "1" - number cannot be incremented - Memcached is ASSHOLE

	private final MemcachedClient client;

	private final boolean clientCreated;

	private final boolean namespaceVersioning;

	private long operationTimeout = 5000; //Memcached call timeout in milliseconds

	public SpyMemcache(String name, ConnectionFactory connectionFactory, List<InetSocketAddress> addrs,
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

	public SpyMemcache(String name, MemcachedClient client, long operationTimeout, TimeUnit unitOfTimeout) {
		this(name, client, operationTimeout, unitOfTimeout, false);
	}

	public SpyMemcache(String name, MemcachedClient client) {
		this(name, client, 5, TimeUnit.SECONDS, false);
	}

	public SpyMemcache(String name, MemcachedClient client, boolean namespaceVersioning) {
		this(name, client, 5, TimeUnit.SECONDS, namespaceVersioning);
	}

	public SpyMemcache(String name, MemcachedClient client, long operationTimeout, TimeUnit unitOfTimeout,
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

	public void setOperationTimeout(long timeout, TimeUnit unit) {
		this.operationTimeout = unit.toMillis(timeout);
		if (this.operationTimeout < 1000) {
			throw new IllegalArgumentException("Operation timeout " + operationTimeout + " must be >= 1 second");
		}
	}

	@Override
	protected CacheEntry<V> doGet(String cacheKey) throws Exception {
		if (cacheKey.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		Future<Object> future = client.asyncGet(cacheKey);
		try {
			return (CacheEntry<V>) future.get(operationTimeout, TimeUnit.MILLISECONDS);
		} catch (CheckedOperationTimeoutException cotx) {
			logger.warn("GET operation timeout: " + operationTimeout + " millis, Key: " + cacheKey);
			return null;
		}

	}

	@Override
	protected Boolean doSet(String cacheKey, CacheEntry<V> entry) throws Exception {
		if (cacheKey.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		int ttlMillis = (int) entry.getEvictTtl();
		Future<Boolean> future = client.set(cacheKey, ttlMillis, entry);
		try {
			return future.get(operationTimeout, TimeUnit.MILLISECONDS);
		} catch (CheckedOperationTimeoutException cotx) {
			logger.warn("SET operation timeout: " + operationTimeout + " millis, Key: " + cacheKey);
			return false;
		}
	}

	@Override
	protected Boolean doRemove(String cacheKey) throws Exception {
		if (cacheKey.length() > MaxKeyLength) {
			throw new IllegalArgumentException("Key length exceded maximum " + MaxKeyLength);
		}
		Future<Boolean> future = client.delete(cacheKey);
		try {
			return future.get(operationTimeout, TimeUnit.MILLISECONDS);
		} catch (CheckedOperationTimeoutException cotx) {
			logger.warn("DELETE operation timeout: " + operationTimeout + " millis, Key: " + cacheKey);
			return null;
		}
	}

	@Override
	public String getCacheKey(String userKey) {
		String namespace = getNamespace();
		//use MD5 hash if the key is too long, but keep the namespace
		if (namespace.length() + userKey.length() > MaxKeyLength) {
			return namespace + ":" + Cutils.md5hex(userKey);
		} else {
			return namespace + ":" + userKey;
		}
	}

	/**
	 * Changing namespace by incrementing it's version results in (virtally) removing all entries means  
	 * because namespace is part of key - all keys became invalid
	 */
	@Override
	public void removeAll() throws IllegalStateException {
		if (namespaceVersioning) {
			String nsVersionKey = getNsVersionKey();
			long incr = client.incr(nsVersionKey, 1);
			if (incr == -1) {//nsVersion entry does not not exist -> create it
				try {
					Future<Boolean> future = client.set(nsVersionKey, DAY, VERSION_IN);
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
		super.close();
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
	 * Namespace is used as prefix for every key to avoid key clashes with other caches
	 */
	public String getNamespace() {
		if (namespaceVersioning) {
			return getName() + ":" + getNsVersion();
		} else {
			return getName();
		}
	}

	private String getNsVersion() {
		//TODO cache nsVersion localy for X seconds - use read/write lock on last checked timestamp 
		String nsVersionKey = getNsVersionKey();
		try {
			Future<Object> gfuture = client.asyncGet(nsVersionKey);
			String nsVersion = (String) gfuture.get(operationTimeout, TimeUnit.MILLISECONDS);
			int icnt = 0;
			while (nsVersion == null && ++icnt < 5) {
				Future<Boolean> afuture = client.add(nsVersionKey, DAY, VERSION_IN);
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
