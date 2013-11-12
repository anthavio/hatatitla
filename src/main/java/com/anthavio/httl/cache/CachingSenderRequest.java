package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.cache.CacheLoadRequest;
import com.anthavio.cache.CacheEntryLoader.MissingExceptionSettings;
import com.anthavio.httl.SenderRequest;

/**
 * @author martin.vanek
 *
 */
public class CachingSenderRequest {

	private final SenderRequest senderRequest;

	private final boolean missingLoadAsync;

	private final boolean expiredLoadAsync;

	private final MissingExceptionSettings syncExceptionSettings;

	private final MissingExceptionSettings asyncExceptionSettings;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final String userKey; //caller takes responsibility for key uniqueness
	
	private long lastRefresh; //only for RefreshMode.SCHEDULED 

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
	public CachingSenderRequest(SenderRequest request, boolean missingLoadAsync, boolean expiredLoadAsync,
			MissingExceptionSettings syncExceptionSettings, MissingExceptionSettings asyncExceptionSettings, long hardTtl, long softTtl,
			TimeUnit unit, String userKey) {
		if (request == null) {
			throw new IllegalArgumentException("null request");
		}
		this.senderRequest = request;

		this.missingLoadAsync = missingLoadAsync;
		this.expiredLoadAsync = expiredLoadAsync;

		if ((missingLoadAsync || expiredLoadAsync) && asyncExceptionSettings == null) {
			throw new IllegalArgumentException("Async ExceptionSettings must not be null");
		}
		this.asyncExceptionSettings = asyncExceptionSettings;

		if ((!missingLoadAsync || !expiredLoadAsync) && syncExceptionSettings == null) {
			throw new IllegalArgumentException("Sync ExceptionSettings must not be null");
		}
		this.syncExceptionSettings = syncExceptionSettings;

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

		if (userKey != null && userKey.length() == 0) {
			throw new IllegalArgumentException("Custom cache key CAN be null, but MUST NOT be EMPTY string");
		}
		this.userKey = userKey;
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

	public boolean isMissingLoadAsync() {
		return missingLoadAsync;
	}

	public boolean isExpiredLoadAsync() {
		return expiredLoadAsync;
	}

	public String getUserKey() {
		return userKey;
	}

	protected long getLastRefresh() {
		return lastRefresh;
	}

	protected void setLastRefresh(long executed) {
		this.lastRefresh = executed;
	}
	
	public MissingExceptionSettings getSyncExceptionSettings() {
		return syncExceptionSettings;
	}

	public MissingExceptionSettings getAsyncExceptionSettings() {
		return asyncExceptionSettings;
	}

	@Override
	public String toString() {
		return "CachingRequest [hardTtl=" + hardTtl + ", softTtl=" + softTtl + "]";
	}

}
