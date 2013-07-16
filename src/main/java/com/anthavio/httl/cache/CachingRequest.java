package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.Cache.RefreshMode;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.cache.CachingRequestBuilders.CachingRequestBuilder;

/**
 * TODO try to inherit from CacheRequest
 * 
 * @author martin.vanek
 *
 */
public class CachingRequest {

	public static CachingRequestBuilder Builder(CachingSender csender, SenderRequest request) {
		return new CachingRequestBuilder(csender, request);
	}

	private final SenderRequest senderRequest;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final RefreshMode refreshMode;

	private long lastRefresh; //only for RefreshMode.SCHEDULED 

	private final String customCacheKey; //caller takes responsibility for key uniqueness 

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit) {
		this(request, hardTtl, hardTtl, unit, RefreshMode.BLOCK, null); //hardTtl = softTtl
	}

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit, RefreshMode refreshMode) {
		this(request, hardTtl, hardTtl, unit, refreshMode, null);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit) {
		this(request, hardTtl, softTtl, unit, RefreshMode.BLOCK, null);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit, RefreshMode refreshMode) {
		this(request, hardTtl, softTtl, unit, refreshMode, null);
	}

	/**
	 * Full constructor - Use CachingRequestBuilder instead of this...
	 * 
	 * @param request - request to request a request
	 * @param hardTtl - cache entry expiry time 
	 * @param softTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * @param refreshMode - how to refresh stale entry 
	 * @param customCacheKey - custom cache key to be used instead of standard key derived from request url
	 */
	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit, RefreshMode refreshMode,
			String customCacheKey) {
		if (request == null) {
			throw new IllegalArgumentException("null request");
		}
		this.senderRequest = request;

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

		if (customCacheKey != null && customCacheKey.length() == 0) {
			throw new IllegalArgumentException("Custom cache key CAN be null, but MUST NOT be EMPTY string");
		}
		this.customCacheKey = customCacheKey;
	}

	public SenderRequest getSenderRequest() {
		return senderRequest;
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

	public String getCacheKey() {
		return customCacheKey;
	}

	protected long getLastRefresh() {
		return lastRefresh;
	}

	protected void setLastRefresh(long executed) {
		this.lastRefresh = executed;
	}

	@Override
	public String toString() {
		return "CachingRequest [hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", refreshMode=" + refreshMode + "]";
	}

}
