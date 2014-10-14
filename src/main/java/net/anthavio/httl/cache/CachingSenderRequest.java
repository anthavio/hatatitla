package net.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import net.anthavio.cache.ConfiguredCacheLoader.ExpiredFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.MissingFailedRecipe;
import net.anthavio.httl.HttlRequest;

/**
 * @author martin.vanek
 *
 */
public class CachingSenderRequest {

	private final HttlRequest senderRequest;

	private final boolean missingLoadAsync;

	private final boolean expiredLoadAsync;

	private final MissingFailedRecipe missingFailRecipe;

	private final ExpiredFailedRecipe expiredFailRecipe;

	private final long evictTtl; //seconds

	private final long expiryTtl; //seconds

	private final String userKey; //caller takes responsibility for key uniqueness

	/**
	 * Synchronous loading with single TTL
	 */
	public CachingSenderRequest(HttlRequest senderRequest, int evictTtl, TimeUnit unit) {
		this(senderRequest, false, MissingFailedRecipe.SYNC_STRICT, false, ExpiredFailedRecipe.SYNC_RETURN, evictTtl,
				evictTtl, unit, null);
	}

	/**
	 * Synchronous loading with both TTLs
	 */
	public CachingSenderRequest(HttlRequest senderRequest, int evictTtl, int expiryTtl, TimeUnit unit) {
		this(senderRequest, false, MissingFailedRecipe.SYNC_STRICT, false, ExpiredFailedRecipe.SYNC_RETURN, evictTtl,
				expiryTtl, unit, null);
	}

	/**
	 * Full constructor - Use CachingRequestBuilder instead of this...
	 * 
	 * @param senderRequest - request to request a request
	 * @param missingLoadAsync - Asynchronous load of the missing cache entry
	 * @param missingFailRecipe - What to do on missing entry load failure
	 * @param expiredLoadAsync - Asynchronous load of the expired cache entry
	 * @param expiredFailRecipe - What to do on expired entry load failure
	 * @param evictTtl - cache entry expiry time 
	 * @param validTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * @param userKey - custom cache key to be used instead of standard key derived from request url
	 */
	public CachingSenderRequest(HttlRequest senderRequest, boolean missingLoadAsync,
			MissingFailedRecipe missingFailRecipe, boolean expiredLoadAsync, ExpiredFailedRecipe expiredFailRecipe,
			long evictTtl, long expiryTtl, TimeUnit unit, String userKey) {

		if (senderRequest == null) {
			throw new IllegalArgumentException("Null SenderRequest");
		}
		this.senderRequest = senderRequest;

		this.missingLoadAsync = missingLoadAsync;

		this.missingFailRecipe = missingFailRecipe;

		this.expiredLoadAsync = expiredLoadAsync;

		this.expiredFailRecipe = expiredFailRecipe;

		evictTtl = unit.toSeconds(evictTtl);
		if (evictTtl <= 0) {
			throw new IllegalArgumentException("evictTtl " + evictTtl + " must be >= 1 second ");
		}
		this.evictTtl = evictTtl;

		expiryTtl = unit.toSeconds(expiryTtl);
		if (evictTtl < expiryTtl) {
			throw new IllegalArgumentException("evictTtl " + evictTtl + " must be >= expiryTtl " + expiryTtl);
		}
		this.expiryTtl = expiryTtl;

		if (userKey != null && userKey.length() == 0) {
			throw new IllegalArgumentException("Custom cache key CAN be null, but MUST NOT be EMPTY string");
		}
		this.userKey = userKey;
	}

	public HttlRequest getSenderRequest() {
		return senderRequest;
	}

	public long getEvictTtl() {
		return evictTtl;
	}

	public long getExpiryTtl() {
		return expiryTtl;
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

	public MissingFailedRecipe getMissingRecipe() {
		return missingFailRecipe;
	}

	public ExpiredFailedRecipe getExpiredRecipe() {
		return expiredFailRecipe;
	}

	@Override
	public String toString() {
		return "CachingRequest [evictTtl=" + evictTtl + ", expiryTtl=" + expiryTtl + "]";
	}

}
