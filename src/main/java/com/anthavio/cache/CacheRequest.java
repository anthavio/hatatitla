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

	private final String userKey;

	private final CacheEntryLoader<V> loader;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final RefreshMode refreshMode;

	private long lastRefresh; //only for RefreshMode.SCHEDULED

	/**
	 * Full constructor - Use CachingRequestBuilder instead of this...
	 * 
	 * @param userKey - user cache key
	 * @param loader - updater to fetch expired entry or refresh stale cache value 
	 * @param hardTtl - cache entry expiry time 
	 * @param softTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * @param refreshMode - how to refresh stale entry 
	 * 
	 */
	public CacheRequest(String userKey, CacheEntryLoader<V> loader, long hardTtl, long softTtl, TimeUnit unit,
			RefreshMode refreshMode) {

		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Cache key is blank");
		}
		this.userKey = userKey;

		if (loader == null) {
			throw new IllegalArgumentException("null loader");
		}
		this.loader = loader;

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

	public CacheEntryLoader<V> getLoader() {
		return loader;
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

	@Override
	public String toString() {
		return "CacheRequest [userKey=" + userKey + ", hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", refreshMode="
				+ refreshMode + "]";
	}

}
