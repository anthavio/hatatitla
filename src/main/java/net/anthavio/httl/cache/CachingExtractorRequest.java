package net.anthavio.httl.cache;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.ConfiguredCacheLoader.ExpiredFailedRecipe;
import net.anthavio.cache.ConfiguredCacheLoader.MissingFailedRecipe;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<V extends Serializable> extends CachingSenderRequest {

	private final HttlResponseExtractor<V> extractor;

	private final Class<V> resultType;

	/**
	 * Synchronous loading with single TTL
	 */
	public CachingExtractorRequest(HttlResponseExtractor<V> extractor, HttlRequest senderRequest, int evictTtl,
			TimeUnit unit) {
		this(extractor, senderRequest, false, MissingFailedRecipe.SYNC_STRICT, false, ExpiredFailedRecipe.SYNC_RETURN,
				evictTtl, evictTtl, unit, null);
	}

	/**
	 * Synchronous loading with both TTLs
	 */
	public CachingExtractorRequest(HttlResponseExtractor<V> extractor, HttlRequest senderRequest, int evictTtl,
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
	public CachingExtractorRequest(HttlResponseExtractor<V> extractor, HttlRequest senderRequest,
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
	public CachingExtractorRequest(Class<V> resultType, HttlRequest senderRequest, boolean missingLoadAsync,
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

	public HttlResponseExtractor<V> getExtractor() {
		return extractor;
	}

	public Class<V> getResultType() {
		return resultType;
	}

}
