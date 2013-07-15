package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheRequest<V> {

	public static interface CacheEntryUpdater<V> {

		public V fetch(CacheRequest<V> request);

	}

	private final String userKey;

	private final CacheEntryUpdater<V> updater;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final RefreshMode refreshMode;

	private long lastRefresh; //only for RefreshMode.SCHEDULED

	/**
	 * Full constructor - Use CachingRequestBuilder instead of this...
	 * 
	 * @param userKey - user cache key
	 * @param updater - updater to fetch expired entry or refresh stale cache value 
	 * @param hardTtl - cache entry expiry time 
	 * @param softTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * @param refreshMode - how to refresh stale entry 
	 * 
	 */
	public CacheRequest(String userKey, CacheEntryUpdater<V> updater, long hardTtl, long softTtl, TimeUnit unit,
			RefreshMode refreshMode) {

		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Cache key is blank");
		}
		this.userKey = userKey;

		if (updater == null) {
			throw new IllegalArgumentException("null updater");
		}
		this.updater = updater;

		hardTtl = unit.toSeconds(hardTtl);
		if (hardTtl <= 0) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= 1 second ");
		}
		this.hardTtl = hardTtl;

		softTtl = unit.toSeconds(softTtl);
		if (hardTtl < softTtl) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= softTtl " + softTtl);
		}
		this.softTtl = softTtl;

		if (refreshMode == null) {
			throw new IllegalArgumentException("Refresh mode is null");
		}
		this.refreshMode = refreshMode;

	}

	public String getUserKey() {
		return userKey;
	}

	public CacheEntryUpdater<V> getUpdater() {
		return updater;
	}

	public long getHardTtl() {
		return hardTtl;
	}

	protected long getHardExpire() {
		return lastRefresh + (hardTtl * 1000);
	}

	public long getSoftTtl() {
		return softTtl;
	}

	protected long getSoftExpire() {
		return lastRefresh + (softTtl * 1000);
	}

	public RefreshMode getRefreshMode() {
		return refreshMode;
	}

	protected long getLastRefresh() {
		return lastRefresh;
	}

	protected void setLastRefresh(long executed) {
		this.lastRefresh = executed;
	}

	public boolean isAsyncRefresh() {
		return refreshMode == RefreshMode.SCHEDULED || refreshMode == RefreshMode.REQUEST_ASYN;
	}

	@Override
	public String toString() {
		return "CacheRequest [hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", refreshMode=" + refreshMode + "]";
	}

}
