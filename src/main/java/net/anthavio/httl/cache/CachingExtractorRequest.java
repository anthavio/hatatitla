package net.anthavio.httl.cache;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.ConfiguredCacheLoader.ExpiredFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.MissingFailedRecipe;
import net.anthavio.httl.ResponseExtractor;
import net.anthavio.httl.HttlRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<T extends Serializable> extends CachingSenderRequest {

	private final ResponseExtractor<T> extractor;

	private final Class<T> resultType;

	/**
	 * Synchronous loading with single TTL
	 */
	public CachingExtractorRequest(ResponseExtractor<T> extractor, HttlRequest senderRequest, int evictTtl,
			TimeUnit unit) {
		this(extractor, senderRequest, false, MissingFailedRecipe.SYNC_STRICT, false, ExpiredFailedRecipe.SYNC_RETURN,
				evictTtl, evictTtl, unit, null);
	}

	/**
	 * Synchronous loading with both TTLs
	 */
	public CachingExtractorRequest(ResponseExtractor<T> extractor, HttlRequest senderRequest, int evictTtl,
			int expiryTtl, TimeUnit unit) {
		this(extractor, senderRequest, false, MissingFailedRecipe.SYNC_STRICT, false, ExpiredFailedRecipe.SYNC_RETURN,
				evictTtl, expiryTtl, unit, null);
	}

	/**
	 * 
	 * @param extractor
	 * @param senderRequest
	 * @param missingLoadAsync
	 * @param missingFailRecipe
	 * @param expiredLoadAsync
	 * @param expiredFailRecipe
	 * @param evictTtl
	 * @param expiryTtl
	 * @param unit
	 * @param userKey
	 */
	public CachingExtractorRequest(ResponseExtractor<T> extractor, HttlRequest senderRequest,
			boolean missingLoadAsync, MissingFailedRecipe missingFailRecipe, boolean expiredLoadAsync,
			ExpiredFailedRecipe expiredFailRecipe, long evictTtl, long expiryTtl, TimeUnit unit, String userKey) {
		super(senderRequest, missingLoadAsync, missingFailRecipe, expiredLoadAsync, expiredFailRecipe, evictTtl, expiryTtl,
				unit, userKey);
		if (extractor == null) {
			throw new IllegalArgumentException("Null ResponseBodyExtractor");
		}
		this.extractor = extractor;
		this.resultType = null;
	}

	/**
	 * 
	 * @param resultType
	 * @param senderRequest
	 * @param missingLoadAsync
	 * @param missingFailRecipe
	 * @param expiredLoadAsync
	 * @param expiredFailRecipe
	 * @param evictTtl
	 * @param expiryTtl
	 * @param unit
	 * @param userKey
	 */
	public CachingExtractorRequest(Class<T> resultType, HttlRequest senderRequest, boolean missingLoadAsync,
			MissingFailedRecipe missingFailRecipe, boolean expiredLoadAsync, ExpiredFailedRecipe expiredFailRecipe,
			long evictTtl, long expiryTtl, TimeUnit unit, String userKey) {
		super(senderRequest, missingLoadAsync, missingFailRecipe, expiredLoadAsync, expiredFailRecipe, evictTtl, expiryTtl,
				unit, userKey);
		if (resultType == null) {
			throw new IllegalArgumentException("Null resultType Class");
		}
		this.resultType = resultType;
		this.extractor = null;
	}

	public ResponseExtractor<T> getExtractor() {
		return extractor;
	}

	public Class<T> getResultType() {
		return resultType;
	}

}
