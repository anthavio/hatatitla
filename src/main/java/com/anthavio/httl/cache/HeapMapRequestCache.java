package com.anthavio.httl.cache;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class HeapMapRequestCache<V> extends RequestCache<V> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private TtlEvictingThread ttlEvictingThread;

	private Map<String, CacheEntry<V>> storage = new ConcurrentHashMap<String, CacheEntry<V>>();

	public HeapMapRequestCache() {
		this(0, null);
	}

	public HeapMapRequestCache(int evictionInterval, TimeUnit evictionUnit) {
		super("SIMPLE");
		if (evictionInterval > 0) {
			ttlEvictingThread = new TtlEvictingThread(evictionInterval, evictionUnit);
			ttlEvictingThread.start();
		}
	}

	@Override
	protected CacheEntry<V> doGet(String key) {
		CacheEntry<V> entry = this.storage.get(key);
		if (entry == null) {
			return null;
		} else if (entry.getHardExpire() < System.currentTimeMillis()) {
			//silly but true - don't return if it's expired
			storage.remove(key);
			return null;
		} else {
			return entry;
		}
	}

	@Override
	protected Boolean doSet(String key, CacheEntry<V> entry) {
		this.storage.put(key, entry);
		return true;
	}

	@Override
	public Boolean doRemove(String key) {
		CacheEntry<V> entry = this.storage.remove(key);
		return entry != null ? Boolean.TRUE : Boolean.FALSE;
	}

	@Override
	public void removeAll() {
		logger.debug("Cache clear");
		this.storage.clear();
	}

	@Override
	public void close() {
		logger.debug("Cache destroy");
		if (ttlEvictingThread != null) {
			ttlEvictingThread.keepGoing = false;
			ttlEvictingThread.interrupt();
		}
	}

	private class TtlEvictingThread extends Thread {

		private final long interval;

		private boolean keepGoing = true;

		public TtlEvictingThread(int interval, TimeUnit unit) {
			this.interval = unit.toMillis(interval);
			if (this.interval < 1000) {
				throw new IllegalArgumentException("Interval " + this.interval + " must be >= 1000 millis");
			}
			this.setName(HeapMapRequestCache.this.getName() + "-reaper");
			this.setDaemon(true);
		}

		@Override
		public void run() {
			while (keepGoing) {
				try {
					Thread.sleep(interval);
				} catch (InterruptedException ix) {
					if (keepGoing) {
						logger.debug("TtlEvictingThread stopped from sleep");
						return;
					} else {
						logger.warn("TtlEvictingThread interrupted but continue");
					}
				}
				doEviction();
			}
			logger.debug("TtlEvictingThread stopped from work");
		}

		private void doEviction() {
			long now = System.currentTimeMillis();
			try {
				//XXX beware of concurrent changes of storage
				for (Entry<String, CacheEntry<V>> entry : storage.entrySet()) {
					if (entry.getValue().getHardExpire() < now) {
						storage.remove(entry.getKey());
					}
				}
			} catch (Exception x) {
				logger.warn("Exception during eviction", x);
			}
		}
	}

}
